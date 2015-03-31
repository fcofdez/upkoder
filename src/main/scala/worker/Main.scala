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
import akka.event.{LoggingAdapter, Logging}
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.StatusCodes._
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.server.Directives._
import akka.http.unmarshalling.Unmarshal
import akka.japi.Util.immutableSeq
import akka.pattern.ask
import akka.persistence.journal.leveldb.SharedLeveldbJournal
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import akka.util.Timeout
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import spray.json._
import upkoder.upclose.models._
import worker.Master._
import worker.Master


trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: FlowMaterializer

  def config: Config
  val logger: LoggingAdapter

  lazy val upcloseConnectionFlow = Http().outgoingConnection("api.upclose.me").flow

  def upcloseRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(upcloseConnectionFlow).runWith(Sink.head)

  def fetchBroadcastInfo(archive_id: String): Future[Either[String, UpcloseCollection]] = {
    upcloseRequest(RequestBuilding.Get(s"""/broadcasts?filter={\"tokbox_archive_id\":\"$archive_id\"}""")).flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[UpcloseCollection].map(Right(_))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Upclose request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  val routes = {
    pathPrefix("jobs") {
      (post & entity(as[TokboxInfo])) { tokboxInfoRequest =>
        complete {
          fetchBroadcastInfo(tokboxInfoRequest.id).map[ToResponseMarshallable]{
            case Right(upcloseCollection) => Created -> upcloseCollection.collection.head
            case Left(err) => NotFound -> err }
        }
      }
    }
  }
}

trait Backend {
  def startBackend(port: Int, role: String): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", conf)

    startupSharedJournal(system, startStore = (port == 2551), path =
      ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))
    val workTimeout = 2.seconds
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


object Upcoder extends App with Service with Backend{


  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()
  startBackend(2551, "backend")
  Thread.sleep(5000)
  startBackend(2552, "backend")
  Thread.sleep(5000)


  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bind(interface = "0.0.0.0", port = 9000).startHandlingWith(routes)
}
