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
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigFactory
import java.io.IOException
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import spray.json.DefaultJsonProtocol
import worker.Master._


case class TokboxInfo(id: String, status: String, name: Option[String], reason: Option[String], sessionId: Option[String], partnerId: Option[Int], createdAt: Option[Long], size: Option[Int], mode: Option[String], updatedAt: Option[Long], url: Option[String], duration: Option[Int])

case class UpcoderJob(id: String)

case class UpcloseBroadcast(id: Int, account_id: Int)

case class UpcloseCollection(collection: Seq[UpcloseBroadcast])

trait Protocols extends DefaultJsonProtocol {
  implicit val tokboxInfoFormat = jsonFormat12(TokboxInfo.apply)
  implicit val JobFormat = jsonFormat1(UpcoderJob.apply)
  implicit val BroadcastFormat = jsonFormat2(UpcloseBroadcast.apply)
  implicit val UpcloseCollectionFormat = jsonFormat1(UpcloseCollection.apply)
}


trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: FlowMaterializer

  def config: Config
  val logger: LoggingAdapter

  lazy val upcloseConnectionFlow = Http().outgoingConnection("api.upclose.me").flow

  def upcloseRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(upcloseConnectionFlow).runWith(Sink.head)

  def fetchBroadcastInfo(archive_id: String): Future[Either[String, Seq[UpcloseBroadcast]]] = {
    upcloseRequest(RequestBuilding.Get(s"""/broadcasts""")).flatMap { response =>
      response.status match {
        case OK => println(Unmarshal(response.entity)); Unmarshal(response.entity).to[Seq[UpcloseBroadcast]].map(Right(_))
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
          Created -> fetchBroadcastInfo(tokboxInfoRequest.id)
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

    // startupSharedJournal(system, startStore = (port == 2551), path =
    //   ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))
    // val workTimeout = 2.second
    // system.actorOf(ClusterSingletonManager.props(Master.props(workTimeout), "active",
    //   PoisonPill, Some(role)), "master")
  }
}


object Upcoder extends App with Service with Backend{


  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bind(interface = "0.0.0.0", port = 9000).startHandlingWith(routes)
}
