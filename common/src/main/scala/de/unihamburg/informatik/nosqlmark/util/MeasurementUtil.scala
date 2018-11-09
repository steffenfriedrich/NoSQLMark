package de.unihamburg.informatik.nosqlmark.util

import java.io.{ByteArrayOutputStream, InputStream, PrintStream, File => JFile}
import java.text.SimpleDateFormat

import better.files.Dsl.SymbolicOperations
import better.files._
import de.unihamburg.informatik.nosqlmark.api.{CoreJob, CoreJob$, Job}
import org.HdrHistogram._
import org.apache.commons.io.IOUtils
import play.api.libs.json._

/**
  * Created by Steffen Friedrich on 13.10.2015.
  */
object MeasurementUtil {

  def exportMeasurementsToFiles(measurements: Map[String, String], jobID: String, exportFolder: String): Unit = {
    val json = new JsObject(Map("JobID" -> JsString(jobID))) ++ Json.toJson(constructSummaries(measurements)).as[JsObject]
    println(json)
    val output = File(exportFolder).createIfNotExists(true)
    val summaryFile = output / "summary.json"
    summaryFile.createIfNotExists().overwrite(Json.prettyPrint(json))

    val histograms = constructHistograms(measurements)
    histograms.foreach(histogram => {
      val hFile = output / ("hdrhistogram-" + histogram._1.toLowerCase() + ".hdr")
      hFile.createIfNotExists().overwrite(compressHistogram(histogram._2))

      val percentileFile = output / ("percentiles-" + histogram._1.toLowerCase() + ".dat")
      histogram._2.outputPercentileDistribution(new PrintStream(percentileFile.toJava), 2, 1000.0, true);
    })
  }

  def constructSummaries(measurements: Map[String, String]): Map[String, Map[String, String]] = {
    val runtime = measurements("RunTime(ms)")
    val throughput = measurements("Throughput(ops/sec)")
    val histograms = constructHistograms(measurements)

    Map("Overall" -> Map("RunTime(ms)" -> runtime, "Throughput(ops/sec)" -> throughput)) ++
      histograms.map(x => {
        x._1 -> constructSummaryMap(x._2)
      })
  }

  def constructSummaryMap(h: Histogram) = Map(
    ("Count") -> ("" + h.getTotalCount),
    ("MaxValue(ms)" -> ("" + h.getMaxValue / 1000d)),
    ("MinValue(ms)" -> ("" + h.getMinValue / 1000d)),
    ("Mean(ms)" -> ("" + h.getMean / 1000d)),
    ("StdDeviation(ms)" -> ("" + h.getStdDeviation / 1000d)),
    ("90Percentile(ms)" -> ("" + h.getValueAtPercentile(90) / 1000d)),
    ("95Percentile(ms)" -> ("" + h.getValueAtPercentile(95) / 1000d)),
    ("99Percentile(ms)" -> ("" + h.getValueAtPercentile(99) / 1000d)),
    ("99.9Percentile(ms)" -> ("" + h.getValueAtPercentile(99.9) / 1000d)),
    ("99.99Percentile(ms)" -> ("" + h.getValueAtPercentile(99.99) / 1000d))
  )


  def constructHistograms(measurements: Map[String, String]): Map[String, Histogram] = {
    var histograms: Map[String, Histogram] = Map[String, Histogram]()
    measurements.foreach(m => {
      m._1 match {
        case "RunTime(ms)" =>
        case "Throughput(ops/sec)" =>
        case _ => {
          val histogram = new Recorder(3)
          val compressedHistogram: String = m._2
          val intervalHistogram: Histogram = decompressHistogram(compressedHistogram)
          if (intervalHistogram != null) {
            val valueIterator = new RecordedValuesIterator(intervalHistogram)
            while (valueIterator.hasNext) {
              val i = valueIterator.next()
              val count = i.getCountAtValueIteratedTo.toInt
              val value = i.getValueIteratedTo
              for (i <- (1 to count)) histogram.recordValue(value)
            }
          }
          histograms += (m._1 -> histogram.getIntervalHistogram)
        }
      }
    })
    histograms
  }

  def compressHistogram(recorder: Recorder): String = compressHistogram(recorder.getIntervalHistogram)

  def compressHistogram(histogram: Histogram): String = {
    val compressedHistogram: ByteArrayOutputStream = new ByteArrayOutputStream
    val log = new PrintStream(compressedHistogram, false, "UTF-8")
    val histogramLogWriter = new HistogramLogWriter(log)
    histogramLogWriter.outputIntervalHistogram(histogram)
    log.close
    compressedHistogram.toString("UTF8")
  }

  def decompressHistogram(file: File): Histogram = {
    val hlr = new HistogramLogReader(file.toJava)
    var hasNext: Histogram = hlr.nextIntervalHistogram().asInstanceOf[Histogram]
    var intervallHistograms: List[Histogram] = List[Histogram](hasNext)
    while (hasNext != null) {
      hasNext = hlr.nextIntervalHistogram().asInstanceOf[Histogram]
      if (hasNext != null) {
        intervallHistograms = intervallHistograms :+ hasNext
      }
    }
    if (intervallHistograms.size > 1) {
      combineHistograms(intervallHistograms)
    } else intervallHistograms.head
  }

  def decompressHistogram(compressedHistogram: String): Histogram = {
    val stream: InputStream = IOUtils.toInputStream(compressedHistogram, "UTF-8")
    new HistogramLogReader(stream).nextIntervalHistogram.asInstanceOf[Histogram]
  }


  def genPercentileDistributions(batchname: String): Unit = {
    genPercentileDistributions(batchname, 2)
  }

  def genPercentileDistributions(batchname: String, percentileTicksPerHalfDistance: Int): Unit = {
    val batchfolder = "results" / batchname
    if (batchfolder isDirectory) {
      batchfolder.children.foreach(jobFolder => {
        outputAllPercentileDistributions(jobFolder, percentileTicksPerHalfDistance)
      })
    } else println("can't find any result for batchname " + batchname)
  }

  def outputAllPercentileDistributions(folder: File, percentileTicksPerHalfDistance: Int): Unit = {
    if (!folder.isDirectory) {
      // println(folder + " is not a folder")
    } else {
      folder.children.filter(f => f.hasExtension && f.extension.get == ".hdr").foreach(f => {
        outputPercentileDistribution(f, percentileTicksPerHalfDistance: Int)
      })
    }
  }

  def outputPercentileDistribution(hdrFile: File, percentileTicksPerHalfDistance: Int): Unit = {
    val path = hdrFile.parent
    val name = hdrFile.nameWithoutExtension
    val histogram = decompressHistogram(hdrFile)
    val outputFile = path / ("percentiles-" + name.toLowerCase + "-" + percentileTicksPerHalfDistance + ".dat")
    outputPercentileDistribution(histogram, outputFile, percentileTicksPerHalfDistance)
  }

  def outputPercentileDistribution(histogram: Histogram, outputFile: File, percentileTicksPerHalfDistance: Int): Unit = {
    val printStream = new PrintStream(outputFile.toJava)
    histogram.outputPercentileDistribution(printStream, percentileTicksPerHalfDistance, 1000.0, true)
  }

  def combineHistograms(histograms: List[Histogram]): Histogram = {
    val combinedHistogram = new Recorder(3)
    histograms.foreach(histogram => {
      val valueIterator = new RecordedValuesIterator(histogram)
      while (valueIterator.hasNext) {
        val i = valueIterator.next()
        val count = i.getCountAtValueIteratedTo.toInt
        val value = i.getValueIteratedTo
        for (i <- (1 to count)) combinedHistogram.recordValue(value) // other method to combine
      }
    })
    combinedHistogram.getIntervalHistogram
  }

  def createTimeseriesDataFromLogs(logfileName: String) = {
    val dir = File("logs")
    val matches = dir.glob("**/*" + logfileName + "*.log")
    matches.foreach(file => {
      file.lines.foreach(line => {
        val newline = line.split(" ").reverse.head
        val values = newline.split(",")
        val id = values(0).split("-")(0)
        val operation = values(1)
        var outputfile = "logs" / ("timeseries_" + id + "_" + operation + ".dat") createIfNotExists (false)
        outputfile.appendLine(newline)
      })
    })
  }


  def genPlotData(batchname: String): Unit = {
    if ("./results" / batchname isDirectory) {
      Seq("ALL", "READ", "UPDATE", "READ-MODIFY-WRITE", "INSERT", "DELETE").foreach(operation => {
        genPlotData(batchname, operation)
      })
    } else println("can't find any result for batchname " + batchname)
  }

  def genPlotData(batchname: String, operations: Seq[String]): Unit = {
    if ("./results" / batchname isDirectory) {
      operations.foreach(operation => {
        genPlotData(batchname, operation)
      })
    } else println("can't find any result for batchname " + batchname)
  }

  def genPlotData(batchname: String, operation: String): Unit = {
    val batchfolder = "results" / batchname
    if (batchfolder isDirectory) {
      val out = batchfolder / ("plot_data-" + operation.toLowerCase + ".csv")
      try {
        out < "\"RunTime\",\"Throughput\",\"Mean\",\"StdDeviation\",\"Min\",\"Max\",\"90%ile\",\"95%ile\",\"99%ile\",\"99.9%ile\",\"99.99%ile\",\"Target\",\"Nodes\",\"Worker\""
        out << ""
        batchfolder.children.foreach(result => {
          val summary = result / "summary.json"
          val workload = if ((result / "workload.json") exists) result / "workload.json" else result / "workload."
          if (summary.exists && workload.exists) {

            val json = Json.parse(summary.contentAsString)

            val overallRuntime = jsToString(json \ "Overall" \ "RunTime(ms)")
            val overallThroughput = jsToString(json \ "Overall" \ "Throughput(ops/sec)")
            val count = jsToString(json \ operation \ "Count")
            val mean = jsToString(json \ operation \ "Mean(ms)")
            val stdDeviation = jsToString(json \ operation \ "StdDeviation(ms)")
            val min = jsToString(json \ operation \ "MinValue(ms)")
            val max = jsToString(json \ operation \ "MaxValue(ms)")
            val p90 = jsToString(json \ operation \ "90Percentile(ms)")
            val p95 = jsToString(json \ operation \ "95Percentile(ms)")
            val p99 = jsToString(json \ operation \ "99Percentile(ms)")
            val p999 = jsToString(json \ operation \ "99.9Percentile(ms)")
            val p9999 = jsToString(json \ operation \ "99.99Percentile(ms)")

            val job = Job.loadJob(workload)
            //  YCSBWorkloadParser.parse(Array("-f", workload.path.toString))

            out << overallRuntime + "," + overallThroughput + "," + mean + "," + stdDeviation + "," + min + "," +
              max + "," + p90 + "," + p95 + "," + p99 + "," + p999 + "," + p9999 + "," + job.target + "," + job.nodes + "," + job.worker
          }
        })
      } catch {
        case e: Exception => out.delete(true)
      }
    } else println("can't find any result for batchname " + batchname)
  }

  def genYCSBPercentilesAndSummary(path: String) = {
    val folder = File(path)
    try {
      var summaryMap: Map[String, Map[String, String]] = Map()
      var decompressed: List[Histogram] = List()
      var decompressed_intended: List[Histogram] = List()

      folder.children.foreach(hdrfile => if (hdrfile.extension.get == ".hdr") {
        val operationName = hdrfile.nameWithoutExtension.split("hdr")(0)
        if (!operationName.contains("CLEANUP")) {
          val histogram = decompressHistogram(hdrfile)
          if (operationName.contains("Intended")) {
            decompressed_intended = histogram +: decompressed_intended
          } else {
            decompressed = histogram +: decompressed
          }
          summaryMap += (operationName -> constructSummaryMap(histogram))
          outputPercentileDistribution(histogram, folder / ("percentiles-" + operationName + ".dat"), 2)
          outputPercentileDistribution(histogram, folder / ("percentiles-" + operationName + "_3.dat"), 3)
        }
      })

      val combined = combineHistograms(decompressed)
      summaryMap += ("ALL" -> constructSummaryMap(combined))
      outputPercentileDistribution(combined, folder / "percentiles-ycsb-all.dat", 2)
      outputPercentileDistribution(combined, folder / "percentiles-ycsb-all_3.dat", 3)

      val combined_intended = combineHistograms(decompressed_intended)
      summaryMap += ("Intended-ALL" -> constructSummaryMap(combined_intended))
      outputPercentileDistribution(combined_intended, folder / "percentiles-ycsb-intended-all.dat", 2)
      outputPercentileDistribution(combined_intended, folder / "percentiles-ycsb-intended-all_3.dat", 3)

      val json = Json.toJson(summaryMap).as[JsObject]
      val summaryFile = folder / "summary_v2.json"
      summaryFile.createIfNotExists().overwrite(Json.prettyPrint(json))
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }

  def genGCLogPlotData(file: String) = {
    val logfile = File(file)
    val out: File = logfile.parent / (logfile.nameWithoutExtension + "-plot_data.csv")
    out < "\"Timestamp\",\"LSN\",\"GC\""
    out << ""

    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    logfile.lines.foreach(line => {
      if (line.contains("Total time for which application threads were stopped")) {
        val values = line.split(" ")
        if (values(10).toDouble > 0.000999) out << values(0).substring(0, 28) + "," + values(1) + "," + values(10)
      }
    })
  }

  def getHdrHistogramData(histogram: Histogram) = {
    import scala.collection.JavaConverters._


    val values = histogram.recordedValues().asScala
    val pairs = (for (value <- values) yield {
      (value.getDoubleValueIteratedTo, value.getCountAtValueIteratedTo.toDouble)
    })
    pairs.toArray.sortBy(_._1).filter(valCount => valCount._2 > 0.0)
  }

  def jsToString(jstring: JsLookupResult) = jstring.get.toString().replaceAll("\"", "")
}
