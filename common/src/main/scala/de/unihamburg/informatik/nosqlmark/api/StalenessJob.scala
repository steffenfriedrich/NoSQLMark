package de.unihamburg.informatik.nosqlmark.api

import de.unihamburg.informatik.nosqlmark.util.{DateUtil, FlakeIDGen, NetworkUtil}
import play.api.libs.json.{JsObject, JsValue, Json}


object StalenessJobFormats {
  implicit val stalenessProportionsFormat = Json.format[StalenessProportions]
  implicit val stalenessDistributionsFormat = Json.format[StalenessDistributions]
  implicit val stalenessCountsFormat = Json.format[StalenessCounts]
  implicit val stalenessLoadGenerationFormat = Json.format[StalenessLoadGeneration]
  implicit val stalenessJobFormat = Json.format[StalenessJob]
}

import StalenessJobFormats._



/**
  * Created by Steffen Friedrich on 31.01.2017.
  */
case class StalenessJob (
                     // Cluster wide unique id
                     jobID: String = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
                     // Optional name for a group of jobs
                     batchname: String = "",
                     // The name of the workload class to use
                     workload: String = "StalenessWorkload",
                     dbname: String = "LocalSickStoreClient",
                     dbproperties: Map[String, String] = Map(
                  "sickstore.port" -> "54000",
                  "sickstore.timeout" -> "1000",
                  "sickstore.write_concern.ack" -> "2",
                  "sickstore.write_concern.journaling" -> "false",
                  "sickstore.read_preference" -> "secondary",
                  "sickstore.localconfig" -> "./config/sickstore/config_no_delay.yml"
                ),
                     target: Double = 1000.0,
                     // The number of nodes, that should be used for the benchmark.
                     nodes: Int = 1,
                     // The number of worker per node
                     worker: Int = 4,
                     // The name of the database table to run queries against
                     table: String = "usertable",
                     // The column family of fields (required by some databases)
                     columnfamily: String = "family",
                     // Start the transactional, the load or the delete phase
                     // (transactional, load, delete)
                     phase: String = "transactional",
                     //  asynchronously o operationsd to avoid coordinated omission
                     asyncmode: Boolean = true,
                     counts: StalenessCounts = StalenessCounts(),
                     proportions: StalenessProportions = StalenessProportions(),
                     distributions: StalenessDistributions = StalenessDistributions(),
                     loadgeneration: StalenessLoadGeneration = StalenessLoadGeneration(),
                     logmeasurements: Boolean = false,
                     // log free memory, thread count, etc. every 10 seconds for debugging purposes
                     logjvmstats: Boolean = false
              ) extends Job {
  private val targetPerNode = target / nodes.toDouble

  private val warmupPerNode = math.floor(counts.warmupcount / nodes).toInt

  private val opsPerNode = if (phase == "transactional") {
    math.floor(counts.operationcount / nodes).toInt
  }
  else if (counts.insertcount > 0 && counts.insertstart >= 0) {
    math.floor(counts.insertcount / nodes).toInt
  }
  else {
    math.floor(counts.recordcount / nodes).toInt
  }

  private val totalOps = if (phase == "transactional") {
    counts.operationcount
  }
  else if (counts.insertcount > 0 && counts.insertstart >= 0) {
    counts.insertcount
  }
  else {
    counts.recordcount
  }

  /**
    *
    * @return
    */
  def workForNodes: Seq[Job] = {
    def workForNodesIter(acc: Int, work: Seq[StalenessJob]): Seq[StalenessJob] = {
      if (acc >= nodes) work
      else {
        val addOperation = if (acc < totalOps % nodes) 1 else 0
        val addWarmup = if (acc < warmupPerNode % nodes) 1 else 0
        val insertstartForNode = phase match {
          case "transactional" => if (acc == 0) counts.recordcount else {
            work.foldLeft(counts.recordcount)((a, b) => a + b.counts.operationcount)
          }
          case _ => if (acc == 0) 0 else {
            work.foldLeft(0)((a, b) => a + b.counts.operationcount)
          }
        }
        workForNodesIter(acc + 1, this.copy(
          target = targetPerNode,
          counts = counts.copy(
            operationcount = (opsPerNode + addOperation),
            insertcount = if (phase == "transactional") 0 else (opsPerNode + addOperation),
            insertstart = insertstartForNode,
            warmupcount = (warmupPerNode + addWarmup)
          )) +: work)
      }
    }
    workForNodesIter(0, Seq())
  }
}

case class StalenessCounts(// The number of records already in the
                      // user table before the transactional phase (load phase see insert).
                      recordcount: Int = 100000,
                      // number of warmup operations (excluded from measurement)
                      warmupcount: Int = 0,
                      // The number of operations to use during the run phase.
                      operationcount: Int = 10000,
                      // Indicates how many inserts to do
                      // Could support the "insertstart" property, which tells them which record to start at.
                      insertcount: Int = 10000,
                      insertstart: Int = 0,
                      // The number of fields in a record
                      fieldcount: Int = 10,
                      // The size of each field (in bytes)
                      fieldlength: Int = 100,
                      // Should read all field
                      readallfields: Boolean = true,
                      // Should write all fields on update
                      writeallfields: Boolean = true) extends Counts


case class StalenessProportions(readproportion: Double = 0.50,
                       updateproportion: Double = 0.50,
                       insertproportion: Double = 0.0,
                       scanproportion: Double = 0.0,
                       readmodifywriteproportion: Double = 0.0,
                       // Fraction of update- / insert operations which trigger a staleness measurement
                       stalenessmeasurementfraction: Double = 0.01) extends Proportions



case class StalenessDistributions(// The distribution of requests across the keyspace, "zipfian", "uniform", "latest"
                             requestdistribution: String = "zipfian",
                             // Should records be inserted in order "ordered" or pseudo-randomly "hashed"
                             insertorder: String = "hashed",
                             // The distribution used to choose the length of a field
                             fieldlengthdistribution: String = "constant",
                             fieldlengthhistogram: String = "hist.txt",
                             // The distribution used to choose the number of records to access on a scan
                             scanlengthdistribution: String = "uniform",
                             // On a single scan, the maximum number of records to access
                             maxscanlength: Int = 1000,
                             // Percentage of data items that constitute the hot set
                             hotspotdatafraction: Double = 0.2,
                             // Percentage of operations that access the hot set
                             hotspotopnfraction: Double = 0.8,
                             // What percentage of the readings should be within the most recent exponential.frac portion of the dataset?
                             exponentialpercentile: Double = 95.0,
                             // What fraction of the dataset should be accessed exponential.percentile of the time
                             exponentialfrac: Double = 0.8571428571) extends Distributions

case class StalenessLoadGeneration(
// "deadline": method by YCSB; "adaptive": ...
schedulingmethod: String = "adaptive",
// distribution of time between requests ("exponential", "constant" (YCSB) or "gamma").
// note that exponential think-time periods leads to a poisson distributed number of requests in a fixed interval of time.
interrequesttimedistribution: String = "exponential") extends LoadGeneration



