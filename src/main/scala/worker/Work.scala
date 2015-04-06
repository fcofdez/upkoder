package worker

import scala.math._


case class Work(workId: String, archive_url: String) extends Ordered[Work] {
  import scala.math.Ordered.orderingToOrdered

  def compare(that: Work): Int = this.workId compare that.workId
}

case class WorkResult(workId: String, result: Any)
