package de.unihamburg.informatik.nosqlmark.measurements

import java.io.{PrintStream, ByteArrayOutputStream, ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

import akka.actor.{Props, Actor}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.event.Logging
import de.unihamburg.informatik.nosqlmark.api.{ReportingProtocol, JobResult, CommonProtocol}
import ReportingProtocol.StatusReport
import de.unihamburg.informatik.nosqlmark.protocols.MeasurementProtocol
import java.util.{Locale, Scanner}

import org.HdrHistogram._
import org.apache.commons.io.IOUtils

import scala.collection.mutable
import scala.collection.mutable.Map
;

object StatusReporter {
  def props(): Props =
    Props(classOf[StatusReporter])
}

/**
  * Created by Steffen Friedrich on 31.08.2016.
  */
class StatusReporter extends Actor {
  val log = Logging(context.system.eventStream, "StatusReporter")
  val mediator = DistributedPubSub(context.system).mediator

  // jobID -> nodes
  val numberOfNodes = mutable.Map[String, Int]()

  var reports = List[StatusReport]()

  def receive = {

    case ReportingProtocol.StartReporting(jobID, nodes) => numberOfNodes.update(jobID, nodes)

    case sr: ReportingProtocol.StatusReport => {
      val nodes = numberOfNodes.getOrElse(sr.jobID, 1)
      reports = sr :: reports
      val (matched, unmatched) = reports.partition(r => sr.jobID == r.jobID && sr.reportIteration == r.reportIteration)

      if(matched.size == nodes) {
        reports = unmatched
        val report = matched.reduceLeft((a,b) => {
          StatusReport(jobID = a.jobID,
            reportIteration = a.reportIteration,
            throughput = a.throughput + b.throughput,
            estremaining = math.max(a.estremaining, b.estremaining),
            warmingUp = a.warmingUp,
            counts = a.counts + b.counts,
            mean = (a.mean + b.mean) / 2.0,
            min = math.min(a.min, b.min),
            max = math.max(a.max, b.max)
          )
        })
        mediator ! Publish(report.jobID, report)
      }
    }

    case ReportingProtocol.CleanUpReport(jobID) => {
      reports = reports.filterNot(r => r.jobID == jobID)
    }


    case _ =>
  }
}
