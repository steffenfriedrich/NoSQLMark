package de.unihamburg.informatik.nosqlmark.util

import de.unihamburg.informatik.nosqlmark.api.{Job}
import org.HdrHistogram.{Histogram, Recorder}
import play.api.libs.json._

import scala.collection.mutable
import better.files._
import better.files.Dsl.SymbolicOperations

/**
  * Created by Steffen Friedrich on 12.10.2016.
  */
object Scripts {
  import MeasurementUtil._

  def main(args: Array[String]): Unit = {

    // create timeseries *.data files from timeseries log
    if (false) {
      MeasurementUtil.createTimeseriesDataFromLogs("timeseries")
    }
    // output  percentiles
    else if (false) {
      val histogram = MeasurementUtil.decompressHistogram("results" / "20160626_cassandra_3_new_cluster" / "20160626_autoscale_1million" / "2016626_144536_722-101010021-0" / "hdrhistogram-all.hdr")

      println(histogram.getValueAtPercentile(99.99))
      MeasurementUtil.outputPercentileDistribution(histogram, "results" / "20160626_cassandra_3_new_cluster" / "20160626_autoscale_1million" / "2016626_144536_722-101010021-0" / "percentiles-all_3.dat", 3)
    }
    // combine read / update ycsb histograms and output percentiles
    else if (true) {
      ouputYCSBPercentilesAndSummaryForInsert(File("C:/Users/Steffen Friedrich/Desktop/Temp/ycsb-0.11.0-SNAPSHOT/results/ycsb_threads1024_hickup1000_max10000_170228_165234"))
    }
    else if (false) {
      MeasurementUtil.genPlotData("20160930_auto_scale_n2_2_cassandra_nodes")
    }

    else if (false) {
      MeasurementUtil.genGCLogPlotData("./results/20160522_cassandra_coordinated_omission/gc_logs/140/gc-1463564361_0.log")
    }
    // many ycsb folders with different targets (for arkdis mongodb experiments)
    else if (false) {
      genYCSBPlotDataFromJsonArraySummaries("C:/Users/Steffen Friedrich/Desktop/Temp/ycsb-0.11.0-SNAPSHOT/results/ycsb_threads1_hickup1000_max10000_170222_145156")
    }
    else if (false) {
      genNoSQLMarkAndYCSBPlotData("C:/Users/Steffen Friedrich/Desktop/Temp/ycsb-0.11.0-SNAPSHOT/results")
    }
    else if (false) {
      combineAllHistogramsOfSameOperation("C:/Users/Steffen Friedrich/Desktop/Temp/ycsb-0.11.0-SNAPSHOT/results")
    }


    /**
      * Generate YCSB Percentiles and CSV file for plotting. Given a YCSB summary.json exported by the JsonArrayExporter
      */
    def genYCSBPlotDataFromJsonArraySummaries(batchfolderPath: String): Unit = {
      val batchfolder = File(batchfolderPath)
      batchfolder.children foreach (experiment => {
        if (experiment.isDirectory) {
          if (!(experiment / "summary_v2.json").exists) {
            ouputYCSBPercentilesAndSummaryForInsert(experiment)
          }
          var group = "YCSB"
          var overallRuntime = 0
          var overallThroughput = 0.0

          // extract throughput & runtime
          val regex =
            """[^\{\[]*[\{\[](((?<=\{)[^}]*)|((?<=\[)[^\]]*))[\}\]]""".r
          val jsObjectStrg = regex findAllIn (experiment / "summary.json").contentAsString
          jsObjectStrg.foreach(s => {
            Json.parse(s).as[JsArray].value.foreach(metric => {
              if ((metric \ "measurement").toOption.isDefined) {
                val j = (metric \ "measurement").get.toString match {
                  case "\"RunTime(ms)\"" => overallRuntime = (metric \ "value").get.as[Int]
                  case "\"Throughput(ops/sec)\"" => overallThroughput = (metric \ "value").get.as[Double]
                  case _ =>
                }
              }
            })
          })

          val args = (experiment / "program_arguments.sh").contentAsString.split("-")
          val threads = args.find(_.startsWith("threads")).getOrElse("threads 1").split(" ")(1).trim
          val target = args.find(_.startsWith("target")).getOrElse("target 1000").split(" ")(1).trim
          var name = "MongoDB_" + experiment.nameWithoutExtension.split("_").last

          // extract percentiles, etc.
          val summary2 = Json.parse((experiment / "summary_v2.json").contentAsString)
          summary2.as[JsObject].value.foreach(op => {
            val key = op._1.toLowerCase
            if (key != "jobid" && key != "overall") {

              val count = jsToString(op._2 \ "Count")
              val mean = jsToString(op._2 \ "Mean(ms)")
              val stdDeviation = jsToString(op._2 \ "StdDeviation(ms)")
              val min = jsToString(op._2 \ "MinValue(ms)")
              val max = jsToString(op._2 \ "MaxValue(ms)")
              val p90 = jsToString(op._2 \ "90Percentile(ms)")
              val p99 = jsToString(op._2 \ "99Percentile(ms)")
              val p999 = jsToString(op._2 \ "99.9Percentile(ms)")
              val p9999 = jsToString(op._2 \ "99.99Percentile(ms)")

              if (key.contains("intended")) {
                group = "YCSB Intended"
              } else {
                group = "YCSB"
              }

              val out = batchfolder / ("plot_data-" + key.replace("intended-", "") + ".csv")
              if (!out.exists) {
                out < "\"Experiment\",\"Group\",\"RunTime\",\"Throughput\",\"Mean\",\"StdDeviation\",\"Min\",\"Max\",\"90ile\",\"99ile\",\"99.9ile\",\"99.99ile\",\"Target\",\"Nodes\",\"Worker\""
                out << ""
              }
              out << name + "," + group + "," + overallRuntime + "," + overallThroughput + "," + mean + "," + stdDeviation + "," + min + "," + max + "," + p90 + "," + p99 + "," + p999 + "," + p9999 + "," + target + "," + 1 + "," + threads
            }
          })
        }
      })
    }


    /**
      * Generate NoSQLMark,  YCSB Percentiles and CSV files for plotting
      *
      * @param batchfolderPath
      */
    def genNoSQLMarkAndYCSBPlotData(batchfolderPath: String): Unit = {
      val batchfolder = File(batchfolderPath)

      batchfolder.children foreach (experiment => {
        if (experiment.isDirectory) {
          experiment.children.foreach(child => if (child.isDirectory) {{
            child.name match {
              case  childName if childName.startsWith("nosqlmark") => {
                println(childName)
                val summary = Json.parse((child / "summary.json").contentAsString)
                val workload = child / "workload.json"
                val job = Job.loadJob(workload)

                val overall = summary \ "Overall"
                val overallRuntime = jsToString(summary \ "Overall" \ "RunTime(ms)")
                val overallThroughput = jsToString(summary \ "Overall" \ "Throughput(ops/sec)")

                val group = "NoSQLMark"
                var name = childName.split("_").last
                println(name)

                summary.as[JsObject].value.foreach(op => {
                  val key = op._1.toLowerCase
                  if (key != "jobid" && key != "overall") {

                    val count = jsToString(op._2 \ "Count")
                    val mean = jsToString(op._2 \ "Mean(ms)")
                    val stdDeviation = jsToString(op._2 \ "StdDeviation(ms)")
                    val min = jsToString(op._2 \ "MinValue(ms)")
                    val max = jsToString(op._2 \ "MaxValue(ms)")
                    val p90 = jsToString(op._2 \ "90Percentile(ms)")
                    val p99 = jsToString(op._2 \ "99Percentile(ms)")
                    val p999 = jsToString(op._2 \ "99.9Percentile(ms)")
                    val p9999 = jsToString(op._2 \ "99.99Percentile(ms)")

                    val out = batchfolder / ("plot_data-" + key + ".csv")
                    if (!out.exists) {
                      out < "\"Experiment\",\"Group\",\"RunTime\",\"Throughput\",\"Mean\",\"StdDeviation\",\"Min\",\"Max\",\"90ile\",\"99ile\",\"99.9ile\",\"99.99ile\",\"Target\",\"Nodes\",\"Worker\""
                      out << ""
                    }

                    out << name + "," + group + "," + overallRuntime + "," + overallThroughput + "," + mean + "," + stdDeviation + "," + min + "," + max + "," + p90 + "," + p99 + "," + p999 + "," + p9999 + "," + job.target + "," + job.nodes + "," + job.worker
                  }
                })
              }
              case childName if childName.startsWith("ycsb") => {
                println(childName)
                if (!(child / "summary_v2.json").exists) {
                  ouputYCSBPercentilesAndSummaryForInsert(child)
                }

                var name = childName.split("_").last
                var group = "YCSB"
                var overallRuntime = 0
                var overallThroughput = 0.0

                // extract throughput & runtime
                val regex =
                  """[^\{\[]*[\{\[](((?<=\{)[^}]*)|((?<=\[)[^\]]*))[\}\]]""".r
                val jsObjectStrg = regex findAllIn (child / "summary.json").contentAsString
                jsObjectStrg.foreach(s => {
                  val j = (Json.parse(s) \ "measurement").get.toString match {
                    case "\"RunTime(ms)\"" => overallRuntime = (Json.parse(s) \ "value").get.as[Int]
                    case "\"Throughput(ops/sec)\"" => overallThroughput = (Json.parse(s) \ "value").get.as[Double]
                    case _ =>
                  }
                })

                // extract target, threads

                val args = (child / "program_arguments.sh").contentAsString.split("-")
                val threads = args.find(_.startsWith("threads")).getOrElse("threads 1").split(" ")(1).trim
                val target = args.find(_.startsWith("target")).getOrElse("target 1000").split(" ")(1).trim

                // extract percentiles, etc.
                val summary2 = Json.parse((child / "summary_v2.json").contentAsString)
                summary2.as[JsObject].value.foreach(op => {
                  val key = op._1.toLowerCase
                  if (key != "jobid" && key != "overall") {

                    val count = jsToString(op._2 \ "Count")
                    val mean = jsToString(op._2 \ "Mean(ms)")
                    val stdDeviation = jsToString(op._2 \ "StdDeviation(ms)")
                    val min = jsToString(op._2 \ "MinValue(ms)")
                    val max = jsToString(op._2 \ "MaxValue(ms)")
                    val p90 = jsToString(op._2 \ "90Percentile(ms)")
                    val p99 = jsToString(op._2 \ "99Percentile(ms)")
                    val p999 = jsToString(op._2 \ "99.9Percentile(ms)")
                    val p9999 = jsToString(op._2 \ "99.99Percentile(ms)")

                    if (key.contains("intended")) {
                      group = "YCSB Intended"
                    } else { group = "YCSB" }

                    val out = batchfolder / ("plot_data-" + key.replace("-intended", "").replace("intended-", "") + ".csv")
                    if (!out.exists) {
                      out < "\"Experiment\",\"Group\",\"RunTime\",\"Throughput\",\"Mean\",\"StdDeviation\",\"Min\",\"Max\",\"90ile\",\"99ile\",\"99.9ile\",\"99.99ile\",\"Target\",\"Nodes\",\"Worker\""
                      out << ""
                    }



                    out << name + "," + group + "," + overallRuntime + "," + overallThroughput + "," + mean + "," + stdDeviation + "," + min + "," + max + "," + p90 + "," + p99 + "," + p999 + "," + p9999 + "," + target + "," + 1 + "," + threads
                  }
                })

              }
              case _ => None
            }
          }})
        }
      })
    }

    def combineAllHistogramsOfSameOperation(batchfolderPath: String): Unit = {
      val batchfolder = File(batchfolderPath)


      val histograms = mutable.Map[String, List[Histogram]]()

      batchfolder.children foreach (experiment => {
        if (experiment.isDirectory) {
          experiment.children.foreach(hdrfile => if (hdrfile.extension.get == ".hdr") {
            val operationName = hdrfile.nameWithoutExtension.split("hdr")(0)
            val histogram = MeasurementUtil.decompressHistogram(hdrfile)

            if(operationName == "INSERT" && histogram.getValueAtPercentile(90.0)> 33000) println(experiment + " " + operationName + "  " + histogram.getValueAtPercentile(90.0) )

            histograms.put(operationName, histogram :: histograms.getOrElse(operationName, List[Histogram]()))
          })
        }
      })



      var summaryMap: Map[String, Map[String, String]] = Map()

      histograms.foreach{ case (operationName, histogram) => {
        val combinedHistogram = MeasurementUtil.combineHistograms(histogram)
        summaryMap += (operationName -> MeasurementUtil.constructSummaryMap(combinedHistogram) )
        MeasurementUtil.outputPercentileDistribution(combinedHistogram,  batchfolder / ("percentiles-" + operationName + ".dat"), 2)
      }}
      val json = Json.toJson(summaryMap).as[JsObject]
      val summaryFile = batchfolder / "summary_v2.json"
      summaryFile.createIfNotExists().overwrite(Json.prettyPrint(json))
    }
    


    def jsToString(jstring: JsLookupResult) = jstring.getOrElse(JsString("NULL")).toString().replaceAll("\"", "")
  }

  def ouputYCSBPercentilesAndSummaryForInsert(folder: File) = {
    if(folder / "INSERT.hdr" exists) {

      val hInsert = decompressHistogram(folder / "INSERT.hdr")
      outputPercentileDistribution(hInsert, folder / "percentiles-ycsb-insert.dat", 2)
      outputPercentileDistribution(hInsert, folder / "percentiles-ycsb-insert_3.dat", 3)

      val hIntendedInsert = decompressHistogram(folder / "Intended-INSERT.hdr")
      outputPercentileDistribution(hIntendedInsert, folder / "percentiles-intended-insert.dat", 2)
      outputPercentileDistribution(hIntendedInsert, folder / "percentiles-intended-insert_3.dat", 3)

      val summary = Map(
        "INSERT" -> constructSummaryMap(hInsert),
        "INSERT-INTENDED" -> constructSummaryMap(hIntendedInsert)
      )

      val json = Json.toJson(summary).as[JsObject]
      val summaryFile = folder / "summary_v2.json"
      summaryFile.createIfNotExists().overwrite(Json.prettyPrint(json))
    }
  }
}
