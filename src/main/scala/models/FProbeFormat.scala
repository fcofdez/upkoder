package upkoder.models

import spray.json._
import spray.httpx.SprayJsonSupport


case class FFProbeStream(codec_name: Option[String], width: Option[Int], height: Option[Int], codec_type: Option[String])


case class FFProbeFormat(duration: Option[String], bit_rate: Option[String])


case class FFProbeInfo(streams: Seq[FFProbeStream], format: FFProbeFormat)


object FFProbeProtocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val FFProbeStreamFormat = jsonFormat4(FFProbeStream.apply)
  implicit val FFProbeFormatFormat = jsonFormat2(FFProbeFormat.apply)
  implicit val FFProbeInfoFormat = jsonFormat2(FFProbeInfo.apply)

  def ToFFProbeInfo(ffprobe_str: String): FFProbeInfo = {
    ffprobe_str.parseJson.convertTo[FFProbeInfo]
  }
}
