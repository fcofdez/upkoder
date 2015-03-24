package worker

import akka.actor.Actor
import java.io.File
import java.net.URL
import sys.process._
import util.Random.nextInt


case class EncodedVideo(video_url: String, thumbnail_urls: Seq[String])


class WorkExecutor extends Actor {
  def receive = {
    case url: String =>
      val filename = download_video(url)
      val thumbnails = generateThumbnails(filename)
      val video_url = encode(filename)
      sender() ! Worker.WorkComplete(EncodedVideo(video_url, thumbnails))
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

  def encode(filePath: String): String = {
    val outputFilePath = File.createTempFile("video-", ".mp4").getPath()
    Seq("ffmpeg", "-i", filePath, "-strict", "experimental", "-codec:a", "aac", "-b:a", "64k", "-b:v", "1000000", outputFilePath, "-y", "-loglevel", "quiet").!
    outputFilePath
  }

  def generateThumbnails(filePath: String): Seq[String] = {
    Seq.fill(3)(nextInt(getDuration(filePath))).map{ second => generateThumbnail(filePath, second)}
  }

  def download_video(url: String): String = {
    val f = File.createTempFile("video", ".mp4")
    val filename = f.getPath()
    (new URL(url) #> f !!)
    return filename
  }

}
