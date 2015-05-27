package upkoder.upclose.models

import spray.json._
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import scala.math._
import spray.httpx.SprayJsonSupport



object Slug {
  def apply(input:String) = slugify(input)

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\w\\s-]", "") // Remove all non-word, non-space or non-dash characters
      .replace('-', ' ')            // Replace dashes with spaces
      .trim                         // Trim leading/trailing whitespace (including what used to be leading/trailing dashes)
      .replaceAll("\\s+", "_")      // Replace whitespace (including newlines and repetitions) with single dashes
      .toLowerCase                  // Lowercase the final results
  }
}


case class TokboxInfo(id: String, status: String, name: Option[String], reason: Option[String], sessionId: Option[String], partnerId: Option[Int], createdAt: Option[Long], size: Option[Int], mode: Option[String], updatedAt: Option[Long], url: Option[String], duration: Option[Int])

case class UpcoderJob(id: String)

case class UpcloseAccount(id: Int, username: String)

case class UpcloseBroadcast(id: Int, title: Option[String], duration: Int, cumulative_participant_count: Int, created_at: DateTime, tokbox_api_key: String, tokbox_archive_id: String, account: UpcloseAccount) extends Ordered[UpcloseBroadcast] {
  import scala.math.Ordered.orderingToOrdered

  lazy val config = ConfigFactory.load()
  lazy val env = sys.env.get("ENV").getOrElse("dev")
  lazy val originBucket = config.getString(s"upclose.$env.s3.bucket.origin")


  def size: Int = {
    this.duration * 141356
  }

  def username : String = {
    this.account.username
  }

  def archiveName : String = {
    val accountID = this.account.id
    val accountUsername = Slug(this.account.username)
    val broadcastID = this.id
    val broadcastTitle = Slug(this.title.getOrElse("untitled"))
    s"""$accountID-$accountUsername/$broadcastID-$broadcastTitle/"""
  }

  def thumbName(fileName:String): String = {
    this.archiveName + fileName
  }

  def videoArchiveName: String = {
    this.archiveName + "video.mp4"
  }

  def gifName: String = {
    this.archiveName + "video.gif"
  }

  def video_url: String = {
    val tokbox_api_key = this.tokbox_api_key
    val tokbox_archive_id = this.tokbox_archive_id
    s"https://s3.amazonaws.com/$originBucket/$tokbox_api_key/$tokbox_archive_id/archive.mp4"
  }

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
  implicit val AccountFormat = jsonFormat2(UpcloseAccount.apply)
  implicit val BroadcastFormat = jsonFormat8(UpcloseBroadcast.apply)
  implicit val UpcloseCollectionFormat = jsonFormat1(UpcloseCollection.apply)
}
