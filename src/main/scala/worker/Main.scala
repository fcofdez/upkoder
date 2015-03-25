import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.Http
import akka.http.client.RequestBuilding
import akka.http.marshallers.sprayjson.SprayJsonSupport._
import akka.http.marshalling.ToResponseMarshallable
import akka.http.model.{HttpResponse, HttpRequest}
import akka.http.model.StatusCodes._
import akka.http.server.Directives._
import akka.http.unmarshalling.Unmarshal
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import spray.json.DefaultJsonProtocol

case class IpInfo(ip: String, country: Option[String], city: Option[String], latitude: Option[Double], longitude: Option[Double])

trait Protocols extends DefaultJsonProtocol {
  implicit val ipInfoFormat = jsonFormat5(IpInfo.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: FlowMaterializer

  def config: Config
  val logger: LoggingAdapter

  def fetchIpInfo(ip: String): Future[IpInfo] = {
    val x: Future[IpInfo] = Future(IpInfo(ip, Some("asd"), Some("xzc"), None, Some(14.3)))
    x
  }

  val routes = {
    pathPrefix("ip") {
      (get & path(Segment)) { ip =>
        complete {
          fetchIpInfo(ip)
        }
      }
    }
  }
}


object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bind(interface = "0.0.0.0", port = 9000).startHandlingWith(routes)
}
