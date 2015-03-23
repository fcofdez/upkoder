package worker

import akka.actor.Actor

class WorkExecutor extends Actor {
  def receive = {
    case url: String =>
      download_video(url)
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
