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
      log.info("Downloading {}", url)
      val filename = download_video(url)
      log.info("Generating thumbnails {}", filename)
      val thumbnails = generateThumbnails(filename)
      val thumbs = thumbnails.map { uploadToS3(_) }
      log.info("Thumbnail urls {}", thumbs)
      log.info("encoding  {}", filename)
      val video_url = encode(filename)
      val s3_video_url = uploadToS3(video_url).getOrElse("asda")
      log.info("Uploaded video to s3 {}", s3_video_url)
      log.info("asda, {}", getMediaInfo(filename))
      sender() ! Worker.WorkComplete(EncodedVideo(s3_video_url, thumbs))
  }

  def uploadToS3(filePath: String): Option[String] = {
    val file = new java.io.File(filePath)
    val bucket = "upclose-dev-thumbnails"
    s3.bucket(bucket).foreach { _.put(file.getName, file) }
    s3.bucket(bucket).flatMap { s3.getObject(_, file.getName) } map { _.publicUrl.toString }
  }

  def getDuration(filePath: String): Int = {
    val info = Seq("ffprobe", "-i",  filePath, "-show_format", "-loglevel", "quiet")
    info.lines.filter(_.contains("duration")).map(_.replace("duration=", "")).mkString.toDouble.toInt
  }

  def generateThumbnail(filePath: String, second: Int): String = {
    val outputFilePath = File.createTempFile("thumbnail-", ".jpg").getPath()
    val command = Seq("ffmpeg", "-i", filePath, "-deinterlace", "-an", "-ss", second.toString, "-t", "00:00:01", "-r", "1", "-y", "-vcodec", "mjpeg", "-f", "mjpeg", "-loglevel", "quiet", outputFilePath).!
    outputFilePath
 }

  def getMediaInfo(filePath: String): FFProbeInfo = {
    val ffprobe_str = Seq("ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", filePath).!!
    ToFFProbeInfo(ffprobe_str)
  }

  def encode(filePath: String): String = {
    val outputFilePath = File.createTempFile("video-", ".mp4").getPath()
    //ffprobe -v quiet -print_format json -show_format -show_streams thumb0001.png
    Seq("ffmpeg", "-i", filePath, "-strict", "experimental", "-codec:a", "aac", "-b:a", "64k", "-b:v", "1000000", outputFilePath, "-y", "-loglevel", "quiet").!
    outputFilePath
  }

  def generateThumbnails(filePath: String): Seq[String] = {
    Seq.fill(3)(nextInt(getDuration(filePath))).map{ second ⇒ generateThumbnail(filePath, second)}
  }

  def download_video(url: String): String = {
    val f = File.createTempFile("video", ".mp4")
    val filename = f.getPath()
    (new URL(url) #> f !!)
    return filename
  }
}
