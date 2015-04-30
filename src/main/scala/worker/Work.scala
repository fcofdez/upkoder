package worker

import scala.math._
import upkoder.upclose.models.UpcloseBroadcast
import upkoder.models.EncodedVideo


case class Work(workId: String, broadcast: UpcloseBroadcast) extends Ordered[Work] {

  def compare(that: Work): Int = this.broadcast compare that.broadcast
}

case class WorkResult(workId: String, result: EncodedVideo)
