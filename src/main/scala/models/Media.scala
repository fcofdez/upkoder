package upkoder.models

import spray.json._
import spray.httpx.SprayJsonSupport


case class EncodedMedia(url: String, size: Int, weight: Int, height: Int, bitrate: Int, broadcast_id: Int)

case class EncodedVideo(video_url: String, thumbnail_urls: Seq[Option[String]])

trait MediaJsonProtocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val EncodedMediaFormat = jsonFormat6(EncodedMedia.apply)
}
