package worker

import scala.concurrent.duration._
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
import spray.httpx.unmarshalling._
import spray.json._
import spray.routing._
import spray.util._
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

  def postMediaInfo(a: EncodedMedia): Future[HttpResponse] = {
    upcloseRequest(Post(mediaEndpoint("12"), a))
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
      val x = EncodedMedia(url = "https://s3.amazonaws.com/test/asdasd",
        size=12,
      weight=530,
      height=480,
      bitrate=1231,
      broadcast_id=123)
      val a = postMediaInfo(x)
      a onComplete {
        case Success(x) => println("oleeeeeeeeeeeeee {}", x)
        case Failure(e) => log.info("noooooooooooooo {}", e.getMessage)
      }
      log.info("Consumed result: {}", result.video_url)
  }

}
