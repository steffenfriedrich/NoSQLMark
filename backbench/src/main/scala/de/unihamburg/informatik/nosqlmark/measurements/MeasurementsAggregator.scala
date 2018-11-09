package de.unihamburg.informatik.nosqlmark.measurements

import java.io.{PrintStream, ByteArrayOutputStream, ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

import akka.actor.{Props, Actor}
import akka.event.Logging
import de.unihamburg.informatik.nosqlmark.api.CommonProtocol
import de.unihamburg.informatik.nosqlmark.protocols.MeasurementProtocol
import java.util.{Locale, Scanner}

import org.HdrHistogram._
import org.apache.commons.io.IOUtils

import scala.collection.mutable
import scala.collection.mutable.Map
;

object MeasurementsAggregator {
  def props(): Props =
    Props(classOf[MeasurementsAggregator])
}

/**
 * Created by Steffen Friedrich on 31.08.2015.
 */
class MeasurementsAggregator extends Actor {
  val log = Logging(context.system.eventStream, "MeasurementsAggregator")
  var _aggregatedHistogram = new Recorder(3)
  var _histograms = mutable.Map[String, mutable.Map[String, Recorder]]()
  var _runtimes = mutable.Map[String, Double]()
  var _throughputs = mutable.Map[String, Double]()

  def receive = {

    case MeasurementProtocol.Histograms(jobID, masterID, measurements) => {
      log.debug("received histogram for job {} from workermaster {}", jobID, masterID)
      val jobHistograms = _histograms.getOrElse(jobID, mutable.Map[String, Recorder]())
      val runtime = _runtimes.getOrElse(jobID, 0.0)
      val throughput = _throughputs.getOrElse(jobID, 0.0)
      measurements.foreach(m => {
        m._1 match {
          case "RunTime(ms)" => _runtimes.put(jobID, scala.math.max(m._2.toDouble, runtime))
          case "Throughput(ops/sec)" => _throughputs.put(jobID, throughput + m._2.toDouble)
          case _ => {
            val histogram = jobHistograms.getOrElse(m._1, new Recorder(3))
            val compressedHistogram: String = m._2
            val stream: InputStream = IOUtils.toInputStream(compressedHistogram, "UTF-8")
            val intervalHistogram: Histogram = new HistogramLogReader(stream).nextIntervalHistogram.asInstanceOf[Histogram]
            if (intervalHistogram != null) {
              val valueIterator = new RecordedValuesIterator(intervalHistogram)
              while (valueIterator.hasNext) {
                val i = valueIterator.next()
                val count = i.getCountAtValueIteratedTo.toInt
                val value = i.getValueIteratedTo
                for (i <- (1 to count)) histogram.recordValue(value) // recordValueWithCount(value: Long, count: Long
              }
            }
            jobHistograms.put(m._1, histogram)
          }
        }
      })
      _histograms.put(jobID, jobHistograms)
    }


    case MeasurementProtocol.FinishMeasurement(jobID: String, endTime: Long) => {
      val jobHistograms = _histograms.get(jobID)
      if (!jobHistograms.get.isEmpty) {
        val aggregatedResult = jobHistograms.get.map(histogram => {
          val compressedHistogram: ByteArrayOutputStream = new ByteArrayOutputStream
          val log = new PrintStream(compressedHistogram, false, "UTF-8")
          val histogramLogWriter = new HistogramLogWriter(log)
          histogramLogWriter.outputIntervalHistogram(histogram._2.getIntervalHistogram)
          log.close
          histogram._1 -> compressedHistogram.toString("UTF8")
        })
        aggregatedResult.put("Throughput(ops/sec)", _throughputs.get(jobID).get.toString)
        aggregatedResult.put("RunTime(ms)", _runtimes.get(jobID).get.toString)

        sender ! MeasurementProtocol.AggregatedMeasurements(jobID, aggregatedResult.toMap)
      } else sender ! MeasurementProtocol.AggregatedMeasurements(jobID, Map[String, String]((jobID -> "No Result")).toMap)
    }


    case MeasurementProtocol.CleanUpMeasurements(jobID: String) => {
      if (_histograms.contains(jobID)) _histograms.remove(jobID)
      else log.warning("can't delete histograms for job {}", jobID)

      if (_runtimes.contains(jobID)) _runtimes.remove(jobID)
      else log.warning("can't delete runtimes for job {}", jobID)

      if (_throughputs.contains(jobID)) _histograms.remove(jobID)
      else log.warning("can't delete throughputs for job {}", jobID)

      log.debug("dropped histograms for job {}", jobID)
    }

  }

}
