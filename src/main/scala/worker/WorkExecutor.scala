package worker

import akka.actor.{Actor, ActorLogging}
import java.io.File
import java.net.URL
import sys.process._
import util.Random.nextInt
import awscala._, s3._
import com.typesafe.config.ConfigFactory
import upkoder.models.FFProbeProtocols._
import upkoder.models._
import upkoder.upclose.models.UpcloseBroadcast
import scala.language.postfixOps


class WorkExecutor extends Actor with ActorLogging{
  lazy val config = ConfigFactory.load()
  val env = sys.env.get("ENV").getOrElse("dev")
  val region_conf = config.getString(s"upclose.$env.s3.region")
  val thumbBucket = config.getString(s"upclose.$env.s3.bucket.thumbs")
  val videoBucket = config.getString(s"upclose.$env.s3.bucket.videos")
  implicit val s3 = S3()
  s3.setRegion(Region(region_conf))

  def receive = {
    case upcloseBroadcast: UpcloseBroadcast ⇒
      val url = upcloseBroadcast.video_url
      val srcMedia = downloadMedia(url)
      val duration = getDuration(srcMedia.getPath)
      if (duration <= 1)
        sender ! Worker.WorkerRejected(upcloseBroadcast.id)
      else {
        val thumbnails = generateThumbnails(srcMedia, duration)
        val thumbsInfo = thumbnails map { x ⇒ getMediaInfo(x).transformToEncodeMedia(x, uploadToS3(x, thumbBucket, upcloseBroadcast.thumbName(x.getName))) }
        val finalThumsInfo = thumbsInfo map { _.copy(broadcast_id = upcloseBroadcast.id) }
        val encodedMedia = encode(srcMedia.getPath)
        val encodedVideoInfo = getMediaInfo(encodedMedia).transformToEncodeMedia(encodedMedia, uploadToS3(encodedMedia, videoBucket, upcloseBroadcast.videoArchiveName))
        val finalEncodedMediaInfo = encodedVideoInfo.copy(broadcast_id = upcloseBroadcast.id)
        val mediaInfo = finalThumsInfo :+ finalEncodedMediaInfo
        thumbnails.foreach { _.delete }
        encodedMedia.delete
        sender() ! Worker.WorkComplete(EncodedVideo(upcloseBroadcast.id, mediaInfo))
      }
  }

  def uploadToS3(mediaFile: File, bucket: String, fileName: String): Option[String] = {
    s3.bucket(bucket).foreach { _.put(fileName, mediaFile) }
    s3.bucket(bucket).flatMap { s3.getObject(_, fileName) } map { _.publicUrl.toString } map { _.replace("http:", "https:") }
  }

  def getDuration(filePath: String): Int = {
    val info = Seq("ffprobe", "-i",  filePath, "-show_format", "-loglevel", "quiet")
    info.lineStream.filter(_.contains("duration")).map(_.replace("duration=", "")).mkString.toDouble.toInt
  }

  def generateThumbnail(filePath: String, second: Int): File = {
    val thumbnailFile = File.createTempFile("thumbnail-", ".jpg")
    val thumbnailFilePath = thumbnailFile.getPath
    Seq("ffmpeg", "-ss", "4", "-i", filePath, "-deinterlace", "-an", "-ss", second.toString, "-t", "00:00:01", "-r", "1", "-y", "-vcodec", "mjpeg", "-f", "mjpeg", "-loglevel", "quiet", thumbnailFilePath).!!
    thumbnailFile
 }

  def getMediaInfo(media: File): FFProbeInfo = {
    val path = media.getPath
    val ffprobe_str = Seq("ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", path).!!
    ToFFProbeInfo(ffprobe_str)
  }

  def encode(filePath: String): File = {
    val encodedFile = File.createTempFile("video-", ".mp4")
    val encodedFilePath = encodedFile.getPath
    Seq("ffmpeg", "-i", filePath, "-strict", "experimental", "-codec:a", "aac", "-b:a", "64k", "-b:v", "1000000", encodedFilePath, "-y", "-loglevel", "quiet").!
    encodedFile
  }

  def generateThumbnails(srcMedia: File, duration: Int): Seq[File] = {
    Seq.fill(4)(nextInt(duration)) map { generateThumbnail(srcMedia.getPath, _) }
  }

  def downloadMedia(url: String): File = {
    val file = File.createTempFile("video", ".mp4")
    (new URL(url) #> file !!)
    return file
  }
}
