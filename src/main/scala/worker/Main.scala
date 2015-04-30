import akka.actor.{Actor, ActorLogging}
import akka.actor.ActorIdentity
import akka.actor.ActorPath
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.actor.Identify
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.RootActorPath
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterClient
import akka.contrib.pattern.ClusterSingletonManager
import akka.contrib.pattern.ClusterSingletonProxy
import akka.event.{LoggingAdapter, Logging}
import akka.io.IO
import akka.japi.Util.immutableSeq
import akka.pattern.ask
import akka.persistence.journal.leveldb.SharedLeveldbJournal
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import java.util.UUID
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.http.Uri._
import spray.httpx.{SprayJsonSupport, RequestBuilding}
import spray.httpx.marshalling.ToResponseMarshallable
import spray.routing._
import spray.routing.{RoutingSettings, RejectionHandler, ExceptionHandler, HttpService}
import upkoder.upclose.models._
import worker.Master
import worker.WorkExecutor
import worker.WorkResultConsumer
import worker.Worker
import scala.util.{Success, Failure}
import upkoder.services.upclose._


trait Backend {

  def startWorker(): Unit = {
    val conf = ConfigFactory.load("worker")
    val system = ActorSystem("WorkerSystem", conf)
    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) ⇒ system.actorSelection(RootActorPath(addr) / "user" / "receptionist")
    }.toSet

    val clusterClient = system.actorOf(ClusterClient.props(initialContacts), "clusterClient")
    system.actorOf(Worker.props(clusterClient, Props[WorkExecutor]), "worker")
  }


  def startBackend(port: Int, role: String): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", conf)

    startupSharedJournal(system, startStore = (port == 2551), path =
      ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))
    val workTimeout = 7.hour
    system.actorOf(ClusterSingletonManager.props(Master.props(workTimeout), "active",
      PoisonPill, Some(role)), "master")

    if (port == 2551){
      system.actorOf(Props[WorkResultConsumer])
    }

  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore)
      system.actorOf(Props[SharedLeveldbStore], "store")
    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = (system.actorSelection(path) ? Identify(None))
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.shutdown()
    }
    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.shutdown()
    }
  }

  def startHttpService(): Unit = {
    val conf = ConfigFactory.load
    implicit val system = ActorSystem("ClusterSystem", conf)

    val service = system.actorOf(Props[UpcoderServiceActor], "upcoder-service")

    implicit val timeout = Timeout(5.seconds)
    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 9000)
  }

  def startMaster(): Unit = {
    startBackend(2551, "backend")
    Thread.sleep(5000)
    startBackend(2552, "backend")
    Thread.sleep(5000)
    startHttpService()
  }
}


object Upcoder extends App with Backend{

  val role = sys.env.get("ROLE").getOrElse("worker")
  if (role == "master") {
    startMaster()
  } else {
    nProc = Runtime.getRuntime().availableProcessors()
    Seq.fill(nProc * 2) forEach { startWorker() }
  }
}
