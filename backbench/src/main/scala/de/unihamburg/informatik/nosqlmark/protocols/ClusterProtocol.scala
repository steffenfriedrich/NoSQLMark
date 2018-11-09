package de.unihamburg.informatik.nosqlmark.protocols

/**
 * Internal  messages between BackbenchService and Master
 * Created by Friedrich on 04.05.2015.
 */
object ClusterProtocol {

  // messages from Master to BackbenchService
  case class RegisterMaster(masterId: String)

  case class MasterRequestsJob(masterId: String)

  case class JobInitialized(masterId: String, jobId: String)

  case class JobIsDone(masterId: String, jobId: String, histograms: Map[String, String])

  case class JobFailed(masterId: String, jobID: String, throwable: Throwable)

  // messages to Master from BackbenchService
  case class SetID(id: String)

  case class JobIsReady(jobID: String)

  case object RunJob

  case object JobRestart

}
