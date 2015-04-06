package upkoder.upclose.models

import spray.json._
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import com.github.nscala_time.time.Imports._
import scala.math._
import spray.httpx.SprayJsonSupport


case class TokboxInfo(id: String, status: String, name: Option[String], reason: Option[String], sessionId: Option[String], partnerId: Option[Int], createdAt: Option[Long], size: Option[Int], mode: Option[String], updatedAt: Option[Long], url: Option[String], duration: Option[Int])

case class UpcoderJob(id: String)

case class UpcloseBroadcast(id: Int, account_id: Int, cumulative_participant_count: Int, created_at: DateTime, video_url: String) extends Ordered[UpcloseBroadcast] {
  import scala.math.Ordered.orderingToOrdered

  def compare(that: UpcloseBroadcast): Int = (this.cumulative_participant_count, this.created_at) compare (that.cumulative_participant_count, that.created_at)
}

case class UpcloseCollection(collection: Seq[UpcloseBroadcast])


trait Protocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit object DateJsonFormat extends RootJsonFormat[DateTime] {

    private val parserISO : DateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis();

    override def write(obj: DateTime) = JsString(parserISO.print(obj))

    override def read(json: JsValue) : DateTime = json match {
      case JsString(s) => parserISO.parseDateTime(s)
      case _ => throw new DeserializationException("Error info you want here ...")
    }
  }

  implicit val tokboxInfoFormat = jsonFormat12(TokboxInfo.apply)
  implicit val JobFormat = jsonFormat1(UpcoderJob.apply)
  implicit val BroadcastFormat = jsonFormat5(UpcloseBroadcast.apply)
  implicit val UpcloseCollectionFormat = jsonFormat1(UpcloseCollection.apply)
}
