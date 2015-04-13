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



class WorkExecutor extends Actor with ActorLogging{
  lazy val config = ConfigFactory.load()
  val env = sys.env.get("ENV").getOrElse("dev")
  val region_conf = config.getString(s"upclose.$env.s3.region")
  implicit val s3 = S3()
  s3.setRegion(Region(region_conf))

  def receive = {
    case upcloseBroadcast: UpcloseBroadcast ⇒
      val url = upcloseBroadcast.video_url
      val bucket = "upclose-dev-thumbnails"
      log.info("Downloading {}", url)
      val srcMedia = downloadMedia(url)
      log.info("Generating thumbnails {}", srcMedia.getPath)
      val thumbnails = generateThumbnails(srcMedia, upcloseBroadcast.duration)
      log.info("Generated thumbnails {}", srcMedia.getPath)
      val thumbsInfo = thumbnails map { x => getMediaInfo(x).transformToEncodeMedia(x, uploadToS3(x, bucket)) }
      log.info("thumbnails {}", thumbsInfo)
      val finalThumsInfo = thumbsInfo map { _.copy(broadcast_id = upcloseBroadcast.id) }
      log.info("final thumbnails {}", finalThumsInfo)
      val encodedMedia = encode(srcMedia.getPath)
      val buckett = "upclose-dev-videos"
      val encodedVideoInfo = getMediaInfo(encodedMedia).transformToEncodeMedia(encodedMedia, uploadToS3(encodedMedia, buckett))
      val finalEncodedMediaInfo = encodedVideoInfo.copy(broadcast_id = upcloseBroadcast.id)
      val x = finalThumsInfo :+ finalEncodedMediaInfo
      thumbnails.foreach { _.delete }
      encodedMedia.delete
      sender() ! Worker.WorkComplete(EncodedVideo(upcloseBroadcast.id, x))
  }

  def uploadToS3(mediaFile: File, bucket: String): Option[String] = {
    s3.bucket(bucket).foreach { _.put(mediaFile.getName, mediaFile) }
    s3.bucket(bucket).flatMap { s3.getObject(_, mediaFile.getName) } map { _.publicUrl.toString }
  }

  def getDuration(filePath: String): Int = {
    val info = Seq("ffprobe", "-i",  filePath, "-show_format", "-loglevel", "quiet")
    info.lines.filter(_.contains("duration")).map(_.replace("duration=", "")).mkString.toDouble.toInt
  }

  def generateThumbnail(filePath: String, second: Int): File = {
    val thumbnailFile = File.createTempFile("thumbnail-", ".jpg")
    val thumbnailFilePath = thumbnailFile.getPath
    Seq("ffmpeg", "-i", filePath, "-deinterlace", "-an", "-ss", second.toString, "-t", "00:00:01", "-r", "1", "-y", "-vcodec", "mjpeg", "-f", "mjpeg", "-loglevel", "quiet", thumbnailFilePath).!!
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
    Seq.fill(3)(nextInt(getDuration(srcMedia.getPath))).map{ generateThumbnail(srcMedia.getPath, _) }
  }

  def downloadMedia(url: String): File = {
    val file = File.createTempFile("video", ".mp4")
    val filename = file.getPath()
    (new URL(url) #> file !!)
    return file
  }
}
