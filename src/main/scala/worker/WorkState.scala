package worker

import scala.collection.mutable.PriorityQueue
import spray.json._
import upkoder.models.EncodedVideo
import spray.httpx.SprayJsonSupport

object WorkState {

  def empty: WorkState = WorkState(
    pendingWork = PriorityQueue(),
    workInProgress = Map.empty,
    acceptedWorkIds = Set.empty,
    rejectedWorkIds = Set.empty,
    doneWorkIds = Set.empty)

  trait WorkDomainEvent
  case class WorkAccepted(work: Work) extends WorkDomainEvent
  case class WorkStarted(workId: String) extends WorkDomainEvent
  case class WorkRejected(workId: String) extends WorkDomainEvent
  case class WorkCompleted(workId: String, result: EncodedVideo) extends WorkDomainEvent
  case class WorkerFailed(workId: String) extends WorkDomainEvent
  case class WorkerTimedOut(workId: String) extends WorkDomainEvent
}

case class SystemState(pendingWork: Int, doneWork: Int, inProgressWork: Int)

trait SystemStateProtocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val SystemStateFormat = jsonFormat3(SystemState.apply)
}

case class WorkState private (
  private val pendingWork: PriorityQueue[Work],
  private val workInProgress: Map[String, Work],
  private val acceptedWorkIds: Set[String],
  private val rejectedWorkIds: Set[String],
  private val doneWorkIds: Set[String]) {

  import WorkState._

  def hasWork: Boolean = pendingWork.nonEmpty
  def nextWork: Work = pendingWork.head
  def isAccepted(workId: String): Boolean = acceptedWorkIds.contains(workId)
  def isInProgress(workId: String): Boolean = workInProgress.contains(workId)
  def isDone(workId: String): Boolean = doneWorkIds.contains(workId)
  def status(): SystemState = {
    SystemState(pendingWork=pendingWork.size,
      doneWork=doneWorkIds.size,
      inProgressWork=workInProgress.size)
  }

  def updated(event: WorkDomainEvent): WorkState = event match {
    case WorkAccepted(work) ⇒
      pendingWork enqueue work
      copy(
        acceptedWorkIds = acceptedWorkIds + work.workId)
    case WorkStarted(workId) =>
      val work = pendingWork.dequeue
      require(workId == work.workId, s"WorkStarted expected workId $workId == ${work.workId}")
      copy(
        workInProgress = workInProgress + (workId -> work))
    case WorkCompleted(workId, _) =>
      copy(
        workInProgress = workInProgress - workId,
        doneWorkIds = doneWorkIds + workId)
    case WorkRejected(workId) =>
      copy(
        workInProgress = workInProgress - workId,
        rejectedWorkIds = rejectedWorkIds + workId)
    case WorkerFailed(workId) =>
      pendingWork enqueue workInProgress(workId)
      copy(
        workInProgress = workInProgress - workId)
    case WorkerTimedOut(workId) =>
      pendingWork enqueue workInProgress(workId)
      copy(
        workInProgress = workInProgress - workId)
  }
}
