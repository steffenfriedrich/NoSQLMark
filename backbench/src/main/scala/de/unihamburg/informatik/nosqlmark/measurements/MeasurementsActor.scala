package de.unihamburg.informatik.nosqlmark.measurements

import akka.actor.{Actor, Props}
import akka.event.Logging
import de.unihamburg.informatik.nosqlmark.api.Exceptions.MeasurementException
import de.unihamburg.informatik.nosqlmark.api.{Job, ReportingProtocol}
import de.unihamburg.informatik.nosqlmark.api.ReportingProtocol.StatusReport
import de.unihamburg.informatik.nosqlmark.protocols.MeasurementProtocol
import de.unihamburg.informatik.nosqlmark.util.{JvmMonitorUtil, MeasurementUtil}
import org.HdrHistogram.Recorder

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent._
import scala.util.{Failure, Success}

object MeasurementsActor {
  def props(job: Job, masterID: String): Props =
    Props(classOf[MeasurementsActor], job, masterID)
}

/**
  * Created by Steffen Friedrich on 12.10.2015.
  */
class MeasurementsActor(job: Job, masterID: String) extends Actor {
  private val
  log = Logging(context.system.eventStream, "MeasurementsActor")
  private val master = this.context.parent

  private var opsAcked = 0
  private var finish = false

  var reports = List[StatusReport]()
  var measurements = mutable.Map[String, Recorder]()
  var statusReportRecorder = new Recorder(3)
  var throughput: Double = 0

  var starttime: Long = 0
  var tryToFinishTime: Long = 0

  def receive = {
    case MeasurementProtocol.StartMeasurement(jobId, time) => starttime = time

    case MeasurementProtocol.Measure(jobID, operation, latency) => {
      if (operation == "ALL") {
        opsAcked += 1
        statusReportRecorder.recordValue(latency)
      }
      val recorder: Recorder = measurements.getOrElse(operation, new Recorder(3))
      recorder.recordValue(latency)
      measurements.put(operation, recorder)

      if(finish) tryToFinishMeasurement()
    }

    case MeasurementProtocol.FinishMeasurement(jobId, time) => {
      finish = true
      tryToFinishTime = System.nanoTime()
      tryToFinishMeasurement()
    }

    case sr: ReportingProtocol.StatusReport => {
      reports = sr :: reports
      val (matched, unmatched) = reports.partition(r => sr.reportIteration == r.reportIteration)
      if(matched.size == job.worker) {
        val histogram = statusReportRecorder.getIntervalHistogram()
        reports = unmatched
        val report = matched.reduceLeft((a,b) => {
          StatusReport(jobID = a.jobID,
            reportIteration = a.reportIteration,
            throughput = a.throughput + b.throughput,
            estremaining = math.max(a.estremaining, b.estremaining),
            warmingUp = a.warmingUp,
            counts = a.counts,
            mean = a.mean,
            min = a.min,
            max = a.max
          )
        }).copy(counts = histogram.getTotalCount, mean = histogram.getMean / 1000d,
          min = histogram.getMinValue / 1000d, max = histogram.getMaxValue / 1000d)

        if(job.logjvmstats) {
          val jvmstats = JvmMonitorUtil.getJvmStats
          log.info("max memory: " + jvmstats.maxMemory + ", total memory: " + jvmstats.totalMemory + ", used memory: "
            + jvmstats.usedMemory + ", threads: " + jvmstats.threadCount + ", blocking io threads: "
            + jvmstats.ioThreadCount + ", memory per thread: " + jvmstats.memoryPerThread)
        }
        if(report.throughput > 0.0) {
          throughput = report.throughput
          master ! report
        }
      }
    }

    case _ =>
  }

  import context.dispatcher
  def tryToFinishMeasurement() = {
    val actualTime = System.nanoTime()
    val finishingDuration = ((tryToFinishTime - actualTime) nano).toSeconds

    if (opsAcked == job.counts.operationcount && finish) {
      finish = false;
      val result = generateHistogramMap(actualTime)

      if(job.nodes > 1) {
        val f = Future { //ToDo same node different names
          Job.export(job, Job.exportFolder(job) + "_part_backup/" + masterID + "/workload.json")
          MeasurementUtil.exportMeasurementsToFiles(result, job.jobID, Job.exportFolder(job) + "_part_backup/" + masterID)
        }

        f.onComplete {
          case Success(nothing) => {
            log.debug("exported job {} to {}", job.jobID, Job.exportFolder(job) + "_part_backup/" + masterID)
          }
          case Failure(ex) => {
            log.error(ex, "Error occured during export of job {} to {} ... {}", job.jobID, Job.exportFolder(job) + "_part_backup/" + masterID)
          }
        }
      }

      log.debug("send WorkerMaster measurements for {}", job.jobID)
      master ! MeasurementProtocol.Histograms(job.jobID, "", result)
    } else if (finishingDuration > 10) {
      master ! MeasurementProtocol.MeasurementFailed(job.jobID,
        MeasurementException(s"Measurement completion timed out. ($opsAcked / ${job.counts.operationcount} ops acked)"))
    }

  }

  def generateHistogramMap(actualTime: Long) =  {
    val runtime = (actualTime - starttime) / 1000000
    measurements.map(
      opRecorder => opRecorder._1 -> MeasurementUtil.compressHistogram(opRecorder._2)).toMap +
      ("RunTime(ms)" -> ("" + runtime)) + ("Throughput(ops/sec)" -> ("" + throughput))
  }
}
