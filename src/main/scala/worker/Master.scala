package worker

import scala.collection.immutable.Queue
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator
import akka.contrib.pattern.DistributedPubSubMediator.Put
import scala.concurrent.duration.Deadline
import scala.concurrent.duration.FiniteDuration
import akka.actor.Props
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.cluster.Cluster
import akka.persistence.PersistentActor


object Master {
  val ResultsTopic = "results"

  def props(workTimeout: FiniteDuration): Props =
    Props(classOf[Master], workTimeout)

  case class Ack(workId: String)

  private sealed trait WorkerStatus
  private case object Idle extends WorkerStatus
  private case class Busy(workId: String, deadline: Deadline) extends WorkerStatus
  private case class WorkerState(ref: ActorRef, status: WorkerStatus)

  private case object CleanupTick
}


class Master(workTimeout: FiniteDuration) extends PersistentActor with ActorLogging {
  import Master._
  import WorkState._

  val mediator = DistributedPubSubExtension(context.system).mediator
  ClusterReceptionistExtension(context.system).registerService(self)

  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) ⇒ role + "-master"
    case None       ⇒ "master"
  }

  private var workers = Map[String, WorkerState]()

  private var workState = WorkState.empty

  import context.dispatcher
  val cleanupTask = context.system.scheduler.schedule(workTimeout / 2, workTimeout / 2,
    self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()

  override def receiveRecover: Receive = {
    case event: WorkDomainEvent =>
      // only update current state by applying the event, no side effects
      workState = workState.updated(event)
      log.info("Replayed {}", event.getClass.getSimpleName)
  }

  override def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) =>
      if(workers.contains(workerId)){
        workers += (workerId -> workers(workerId).copy(ref = sender()))
      } else {
        log.info("Worker registered: {}", workerId)
        workers += (workerId -> WorkerState(sender(), status = Idle))
        if (workState.hasWork)
          sender() ! MasterWorkerProtocol.WorkIsReady
      }
    case MasterWorkerProtocol.WorkerRequestsWork(workerId) =>
  }



}
