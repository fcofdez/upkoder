package worker

import akka.actor.Actor
import util.Random.nextInt

class WorkExecutor extends Actor {
  def receive = {
    case url: String =>
      val filename = download_video(url)
      val x = encode(filename)
  }

  def getDuration(filePath: String): Int = {
    import sys.process._
    val info = Seq("ffprobe", "-i",  filePath, "-show_format", "-loglevel", "quiet")
    info.lines.filter(_.contains("duration")).map(_.replace("duration=", "")).mkString.toDouble.toInt
  }

  def generateThumbnails(filePath: String) Seq[String] = {
    Seq.fill(3)(nextInt(getDuration(filePath)))
    val command = Seq("ffmpeg", "-i", filePath, "-deinterlace", "-an", "-ss", second, "-t", "00:00:01", "-r" "1", "-y", "-vcodec", "mjpeg", "-f", "mjpeg", "-loglevel", "quiet" target)
  }

  def download_video(url: String): String = {
    import sys.process._
    import java.net.URL
    import java.io.File
    val f = File.createTempFile("video", ".mp4")
    val filename = f.getPath()
    (new URL(url) #> f !!)
    return filename
  }

}
