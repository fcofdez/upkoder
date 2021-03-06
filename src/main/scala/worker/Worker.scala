package worker

import java.util.UUID
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.Terminated
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Stop
import akka.actor.SupervisorStrategy.Restart
import akka.actor.ActorInitializationException
import akka.actor.DeathPactException

import upkoder.models.EncodedVideo

object Worker {

  def props(clusterClient: ActorRef, workExecutorProps: Props, registerInterval: FiniteDuration = 10.seconds): Props =
    Props(classOf[Worker], clusterClient, workExecutorProps, registerInterval)

  case class WorkComplete(result: EncodedVideo)
  case class WorkerRejected(workId: Int)
}

class Worker(clusterClient: ActorRef, workExecutorProps: Props, registerInterval: FiniteDuration)
    extends Actor with ActorLogging{
  import Worker._
  import MasterWorkerProtocol._

  val workerId = UUID.randomUUID().toString

  import context.dispatcher
  val registerTask = context.system.scheduler.schedule(0.seconds, registerInterval, clusterClient,
    SendToAll("/user/master/active", RegisterWorker(workerId)))

  var currentWorkId: Option[String] = None

  val workExecutor = context.watch(context.actorOf(workExecutorProps, "exec"))


  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException           => Stop
    case _: Exception =>
      currentWorkId foreach { workId => sendToMaster(WorkFailed(workerId, workId)) }
      context.become(idle)
      Restart
  }

  def workId: String = currentWorkId match {
    case Some(workId) => workId
    case None         => throw new IllegalStateException("Not working")
  }

  override def postStop(): Unit = registerTask.cancel()


  def receive = idle

  def idle: Receive = {
    case WorkIsReady =>
      sendToMaster(WorkerRequestsWork(workerId))

    case Work(workId, upcloseBroadcast) =>
      log.info("Got work: {}", upcloseBroadcast.video_url)
      currentWorkId = Some(workId)
      workExecutor ! upcloseBroadcast
      context.become(working)
  }

  def waitForWorkRejectedAck() : Receive = {
    case Ack(id) if id == workId =>
      sendToMaster(WorkerRequestsWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)
    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      sendToMaster(WorkRejected(workerId, workId))
  }


  def waitForWorkIsDoneAck(result: EncodedVideo): Receive = {
    case Ack(id) if id == workId =>
      sendToMaster(WorkerRequestsWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)
    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      sendToMaster(WorkIsDone(workerId, workId, result))
  }


  def sendToMaster(msg: Any): Unit = {
    clusterClient ! SendToAll("/user/master/active", msg)
  }

  def working: Receive = {
    case WorkComplete(result) =>
      log.info("Work is complete. Result {}.", result)
      sendToMaster(WorkIsDone(workerId, workId, result))
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck(result))
    case WorkerRejected(workId) =>
      sendToMaster(WorkRejected(workerId, workId.toString))
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkRejectedAck())
    case _: Work =>
      log.info("Worker {} is already working", workerId)
  }
}
