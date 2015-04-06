import akka.actor.Actor
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
// import akka.http.client.RequestBuilding
// import akka.http.marshallers.sprayjson.SprayJsonSupport._
// import akka.http.marshalling.ToResponseMarshallable
// import akka.http.model.StatusCodes._
// import akka.http.model.{HttpResponse, HttpRequest}
// import akka.http.server.Directives._
// import akka.http.unmarshalling.Unmarshal
// import akka.http.{Http => AkkaHttp}
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
import scala.math._
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.http.Uri._
import spray.httpx.{SprayJsonSupport, RequestBuilding}
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.unmarshalling._
import spray.json._
import spray.routing._
import spray.util._
import spray.routing.{RoutingSettings, RejectionHandler, ExceptionHandler, HttpService}
import upkoder.upclose.models._
import worker.Master
import worker.Master._
import worker.Work._
import worker.WorkExecutor
import worker.Worker
import scala.util.{Success, Failure}


object UpcloseService extends Protocols {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher

  // def config: Config
  val logger = Logging(system, getClass)

  lazy val config = ConfigFactory.load()

  val apiUrl = config.getString("upclose.api.url")
  val apiEndpoint = config.getString("upclose.api.endpoint")

  lazy val pipeline = sendReceive ~> unmarshal[UpcloseCollection]

  def upcloseRequest(request: HttpRequest): Future[UpcloseCollection] = pipeline{request}

  def uplcloseUri(archive_id: String): Uri = {
    val query = Query.Cons("filter", s"""{\"tokbox_archive_id\":\"$archive_id\"}""", Query.Empty)
    val auth = Authority(host = Host(apiUrl))
    Uri(scheme = "https", authority = auth, path = Path(apiEndpoint), query = query)
  }

  def fetchBroadcastInfo(archive_id: String): Future[UpcloseCollection] = {
    upcloseRequest(Get(uplcloseUri(archive_id)))
  }
}

class MyServiceActor extends Actor with MyService {
  import worker.Work

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  implicit def actorRefFactory = context
  val masterProxy = context.actorOf(ClusterSingletonProxy.props(
    singletonPath = "/user/master/active",
    role = Some("backend")),
    name = "masterProxy")

  def nextWorkId(): String = UUID.randomUUID().toString

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(routes)
  import context.dispatcher


  def scheduleWork(upcloseBroadcast: UpcloseBroadcast): Int = {
    implicit val timeout = Timeout(5.seconds)
    println("schedule")
    val work = Work(nextWorkId(), upcloseBroadcast.video_url)
    (masterProxy ? work) map {
      case Master.Ack(_) => println("asd")
      case _ => println("nooooooo")
    }
    1
  }
}


trait MyService extends HttpService with Protocols {
  import UpcloseService._

  def scheduleWork(upcloseBroadcast: UpcloseBroadcast): Int

  val routes = {
    pathPrefix("jobs") {
      (post & entity(as[TokboxInfo])) { tokboxInfoRequest =>
        complete {
          fetchBroadcastInfo(tokboxInfoRequest.id).map[ToResponseMarshallable]{ uc =>
            scheduleWork(uc.collection.head)
            uc.collection.head
          }
        }
      }
    }
  }
}


trait Backend {

  def startWorker(port: Int): Unit = {
    // load worker.conf
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
    val workTimeout = 1.hour
    system.actorOf(ClusterSingletonManager.props(Master.props(workTimeout), "active",
      PoisonPill, Some(role)), "master")
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
}


object Upcoder extends App with Backend{

  startBackend(2551, "backend")
  Thread.sleep(5000)
  startBackend(2552, "backend")
  Thread.sleep(5000)
  startWorker(0)
  val conf = ConfigFactory.load
  implicit val system = ActorSystem("ClusterSystem", conf)
  implicit val executor = system.dispatcher


  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)

  // create and start our service actor
  val service = system.actorOf(Props[MyServiceActor], "demo-service")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 9000)
  //Http().bind(interface = "0.0.0.0", port = 9000).startHandlingWith(routes)
}
