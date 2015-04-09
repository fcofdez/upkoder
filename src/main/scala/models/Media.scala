package upkoder.models

import spray.json._
import spray.httpx.SprayJsonSupport


case class EncodedMedia(url: String, size: Int, width: Int, height: Int, bitrate: Int, broadcast_id: Int, mime_type: String)

case class EncodedVideo(broadcast_id: Int, thumbnail_urls: Seq[Option[String]])

trait MediaJsonProtocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val EncodedMediaFormat = jsonFormat7(EncodedMedia.apply)
}
