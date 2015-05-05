package worker

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.ActorLogging
import akka.event.{LoggingAdapter, Logging}
import com.typesafe.config.ConfigFactory
import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator
import upkoder.models.{EncodedMedia, MediaJsonProtocols}
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.http.Uri._
import spray.httpx.{SprayJsonSupport, RequestBuilding}
import spray.httpx.marshalling.ToResponseMarshallable
import spray.routing.{RoutingSettings, RejectionHandler, ExceptionHandler, HttpService}



object UpcloseService extends MediaJsonProtocols{
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher

  // def config: Config
  val logger = Logging(system, getClass)

  lazy val config = ConfigFactory.load()

  val env = sys.env.get("ENV").getOrElse("dev")
  val apiUrl = config.getString(s"upclose.$env.api.url")
  val apiEndpoint = config.getString(s"upclose.$env.api.post_endpoint")

  val pipeline = (
    addHeader("Authorization", "Client 25638abf-fa27-44c8-9a41-2a65ec39ddf") ~> sendReceive
  )

  def upcloseRequest(request: HttpRequest): Future[HttpResponse] = pipeline{request}

  def mediaEndpoint(broadcast_id: String): Uri = {
    val auth = Authority(host = Host(apiUrl))
    Uri(scheme = "https", authority = auth, path = Path(apiEndpoint.replace("$id", broadcast_id)))
  }

  def postMediaInfo(a: EncodedMedia, br_id: Int): Future[HttpResponse] = {
    upcloseRequest(Post(mediaEndpoint(br_id.toString), a))
  }
}


class WorkResultConsumer extends Actor with ActorLogging {
  import UpcloseService._
  import scala.util.{Success, Failure}
  val mediator = DistributedPubSubExtension(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  def receive = {
    case _: DistributedPubSubMediator.SubscribeAck =>
    case WorkResult(workId, result) =>
      result.mediaInfo.foreach { y =>
        val a = postMediaInfo(y, result.broadcast_id)
        a onComplete {
          case Success(x) => log.info("Success Post {}", x)
          case Failure(e) => log.info("Error post {}", e.getMessage)
        }
      }
      log.info("Consumed result: {}", result.broadcast_id)
  }

}
