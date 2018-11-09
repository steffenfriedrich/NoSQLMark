package de.unihamburg.informatik.nosqlmark.protocols

import akka.actor.ActorRef
import com.yahoo.ycsb.Workload

/**
 * Internal messages between WorkerMaster and Worker
 * Created by Steffen Friedrich on 04.05.2015.
 */
object WorkerProtocol {

  // messages from WorkerMaster to himself
  case object Benchmarking

  // messages from Worker to WorkerMaster
  case class Ready(workerID: String)

  case class WorkIsDone(workerID: String, jobID: String)

  case class WorkerFailed(WorkerId: String, jobID: String, throwable: Throwable)

  // messages to Worker from WorkerMaster
  case object Initialize

  case object Run

  // messages between Worker

  // standard YCSB Operations
  sealed trait DoOperation

  case object DoTransaction extends DoOperation

  case object DoInsert extends DoOperation

  case object DoDelete extends DoOperation

}
