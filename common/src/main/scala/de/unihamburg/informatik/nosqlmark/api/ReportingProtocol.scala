package de.unihamburg.informatik.nosqlmark.api

/**
  * Created by Steffen Friedrich on 08.02.2016.
  */
object ReportingProtocol {

  case class StartReporting(jobID: String, numberOfWorkerMaster: Int)

  case class CleanUpReport(jobID: String)

  case class StatusReport(jobID: String,
                          reportIteration: Int,
                          throughput: Double,
                          estremaining: Int,
                          warmingUp: Boolean,
                          counts: Long,
                          mean: Double,
                          min: Double,
                          max: Double)
}
