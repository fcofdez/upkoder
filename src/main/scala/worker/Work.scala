package worker

case class Work(workId: String, archive_url: String, job: Any)

case class WorkResult(workId: String, result: Any)
