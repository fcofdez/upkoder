package upkoder.services.upclose

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
import worker.Worker
import scala.util.{Success, Failure}


object UpcloseService extends Protocols {
  implicit val system = ActorSystem("ServiceSystem")
  implicit val executor = system.dispatcher

  val logger = Logging(system, getClass)

  lazy val config = ConfigFactory.load()

  val env = sys.env.get("ENV").getOrElse("dev")
  val apiUrl = config.getString(s"upclose.$env.api.url")
  val apiEndpoint = config.getString(s"upclose.$env.api.endpoint")

  lazy val pipeline = addHeader("Authorization", "") ~> sendReceive ~> unmarshal[UpcloseCollection]

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


class UpcoderServiceActor extends Actor with UpcoderService with ActorLogging {
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


  def scheduleWork(upcloseBroadcast: UpcloseBroadcast): Unit = {
    implicit val timeout = Timeout(5.seconds)
    log.info("Scheduling")
    val work = Work(nextWorkId(), upcloseBroadcast)
    (masterProxy ? work) map {
      case Master.Ack(_) => log.info("Master ack {}", upcloseBroadcast.video_url)
      case _ => log.info("Master problem {}", upcloseBroadcast.video_url)
    }
  }
}


trait UpcoderService extends HttpService with Protocols {
  import UpcloseService._

  def scheduleWork(upcloseBroadcast: UpcloseBroadcast): Unit

  val routes = {
    pathPrefix("jobs") {
      (post & entity(as[TokboxInfo])) { tokboxInfoRequest =>
        complete {
          if(tokboxInfoRequest.status == "uploaded"){
            fetchBroadcastInfo(tokboxInfoRequest.id).map[ToResponseMarshallable]{ uc =>
              scheduleWork(uc.collection.head)
              uc.collection.head
            }
          }else{
            tokboxInfoRequest
          }
        }
      } ~
      get {
        complete {
          "hola"
        }
      }
    }
  }
}
