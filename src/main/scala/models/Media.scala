package upkoder.models

import spray.json._
import spray.httpx.SprayJsonSupport


case class EncodedMedia(url: String = "", size: Int, width: Int, height: Int, bitrate: Int = 0, broadcast_id: Int = 0, mime_type: String)

case class EncodedVideo(broadcast_id: Int, mediaInfo: Seq[EncodedMedia])

trait MediaJsonProtocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val EncodedMediaFormat = jsonFormat7(EncodedMedia.apply)
}
