package de.unihamburg.informatik.nosqlmark.status

import akka.actor.ActorRef

/**
 * Created by Friedrich on 11.05.2015.
 */
sealed trait WorkerStatus
case object Started extends WorkerStatus
case object WorkInitialized extends WorkerStatus
case class Working(jobID: String) extends WorkerStatus
case class WorkIsDone(jobID: String) extends WorkerStatus
case class WorkerState(ref: ActorRef, status: WorkerStatus)