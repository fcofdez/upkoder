package worker

import upkoder.models.EncodedVideo

object MasterWorkerProtocol {
  // Worker -> Master
  case class RegisterWorker(workerId: String)
  case class WorkerRequestsWork(workerId: String)
  case class WorkIsDone(workerId: String, workId: String, result: EncodedVideo)
  case class WorkFailed(workerId: String, workId: String)
  case class WorkRejected(workerId: String, workId: String)

  // Master -> Workers
  case object WorkIsReady
  case class Ack(id: String)
}
