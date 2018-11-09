package de.unihamburg.informatik.nosqlmark.protocols

import akka.actor.ActorRef
import de.unihamburg.sickstore.backend.anomaly.clientdelay.Throughput

/**
 * Created by Steffen Friedrich on 12.08.2015.
 */
object MeasurementProtocol {

  // messages from MeasurementAggregator to  Backbenchservice
  case class AggregatedMeasurements(jobID: String, measurements: Map[String,String])

  // messages from Worker to MeasurementActor
  case class StartMeasurement(jobID: String, startTime: Long)

  case class FinishMeasurement(jobID: String, endTime: Long)

  case class MeasurementFailed(jobID: String, throwable: Throwable)

  case class OpsDone(ops: Int)

  case class Measure(jobID: String, operation: String, latency: Int)

  case class WarmUpIsDone(workerID: String, jobID: String, opsdone: Int)

  case class CleanUpMeasurements(jobID: String)

  case class Histograms(jobID: String, masterID: String, histograms: Map[String, String])

  case class OpsAcked(opsAcked: Int)

}


