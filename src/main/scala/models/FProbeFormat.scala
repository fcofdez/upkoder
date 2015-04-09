package upkoder.models

import spray.json._
import spray.httpx.SprayJsonSupport
import java.io.File
import upkoder.models._


case class FFProbeStream(codec_type: Option[String], width: Option[Int], height: Option[Int])


case class FFProbeFormat(duration: Option[String], bit_rate: Option[String], format_name: Option[String])


case class FFProbeInfo(streams: Seq[FFProbeStream], format: FFProbeFormat) {
  def mimeType : String = {
    //return "image/jpg"
    if (this.format.format_name.contains("mp4"))
      {
        return "video/mp4"
      }
      else
        return "image/jpg"
  }

  def width: Int = {
    this.streams find { _.codec_type contains "video" } flatMap { _.width } getOrElse 0
  }

  def height: Int = {
    this.streams find { _.codec_type contains "video" } flatMap { _.height } getOrElse 0
  }

  def transformToEncodeMedia(mediaFile: File, url: Option[String]): EncodedMedia = {
    EncodedMedia(size = mediaFile.length.toInt,
      width = this.width,
      height = this.height,
      bitrate = this.format.bit_rate.getOrElse("0").toInt,
      mime_type = this.mimeType,
      url = url.getOrElse("")
    )
  }
}


object FFProbeProtocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val FFProbeStreamFormat = jsonFormat3(FFProbeStream.apply)
  implicit val FFProbeFormatFormat = jsonFormat3(FFProbeFormat.apply)
  implicit val FFProbeInfoFormat = jsonFormat2(FFProbeInfo.apply)

  def ToFFProbeInfo(ffprobe_str: String): FFProbeInfo = {
    ffprobe_str.parseJson.convertTo[FFProbeInfo]
  }
}
