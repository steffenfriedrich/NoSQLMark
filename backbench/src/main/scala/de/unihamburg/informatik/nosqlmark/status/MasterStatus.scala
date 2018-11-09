package de.unihamburg.informatik.nosqlmark.status

import akka.actor.ActorRef

/**
 * Created by Steffen Friedrich on 11.05.2015.
 */
sealed trait MasterStatus

case object Idle extends MasterStatus

case class JobInitialized(jobId: String) extends MasterStatus

case class Benchmarking(jobId: String) extends MasterStatus

case class MasterState(ref: ActorRef, status: MasterStatus)

// Todo check kryo bindings in application.conf