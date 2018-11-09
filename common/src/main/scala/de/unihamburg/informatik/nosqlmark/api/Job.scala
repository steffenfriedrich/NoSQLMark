package de.unihamburg.informatik.nosqlmark.api

import de.unihamburg.informatik.nosqlmark.util.{DateUtil, FlakeIDGen, NetworkUtil}
import play.api.libs.json._

import scala.collection.Seq

/**
  * Created by Friedrich on 26.01.2017.
  */
abstract class Job {
  // Cluster wide unique id
  def jobID: String

  // Optional name for a group of jobs
  def batchname: String

  // The name of the workload class to use
  def workload: String

  def dbname: String

  def dbproperties: Map[String, String]

  def target: Double

  // The number of nodes, that should be used for the benchmark.
  def nodes: Int

  // The number of worker per node
  def worker: Int

  // The name of the database table to run queries against
  def table: String

  // The column family of fields (required by some databases)
  def columnfamily: String

  // Start the transactional, the load or the delete phase
  // (transactional, load, delete)
  def phase: String

  //  asynchronously do operations to avoid coordinated omission
  def asyncmode: Boolean

  def counts: Counts

  def proportions: Proportions

  def distributions: Distributions

  def loadgeneration: LoadGeneration

  // log all measured values
  def logmeasurements: Boolean

  // log free memory, thread count, etc. every 10 seconds for debugging purposes
  def logjvmstats: Boolean

  // compute job parameter for each node given the number of nodes
  def workForNodes: Seq[Job]
}

object Job {

  import CoreJobFormats._
  import StalenessJobFormats._
  import better.files._

  def loadJob(filepath: String) = asCoreJob(loadAbstractJob(filepath))

  def loadJob(file: File) =  asCoreJob(loadAbstractJob(file))

  def asCoreJob(job: Job): CoreJob = job.asInstanceOf[CoreJob]

  def loadAbstractJob(filepath: String) = fromJson(Json.parse(File(filepath).contentAsString))

  def loadAbstractJob(file: File) = fromJson(Json.parse(file.contentAsString))


  def update(job: Job, json: String): Job = {
    val old: JsValue = toJson(job)
    val update: JsValue = Json.parse(json)
    val newJson = Seq(old, update).foldLeft(Json.obj())((obj, a) => {
      obj.deepMerge(a.as[JsObject])
    })
    fromJson(newJson)
  }

  def export(job: Job): Unit = export(job, exportFolder(job) + "/workload.json")

  def export(job: Job, filepath: String): Unit = export(job, File(filepath))

  def export(job: Job, file: File): Unit = {
    file.parent.createIfNotExists(true)
    file.createIfNotExists(false).overwrite(Json.prettyPrint(toJson(job)))
  }

  def prettyPrint(job: Job): Unit = println(Json.prettyPrint(toJson(job)))

  def toJson(job: Job): JsValue = job match {
    case j: CoreJob => Json.toJson(j)
    case j: StalenessJob => Json.toJson(j)
    case _ => throw new RuntimeException(s"Marshalling of job ${job.jobID} to json failed")
  }

  def fromJson(js: JsValue): Job = {
    ((js \ "workload").get.toString.replaceAll("\"","") match {
      case "CoreWorkload" => js.validate[CoreJob]
      case "StalenessWorkload" => js.validate[StalenessJob]
      case s: String => throw new RuntimeException(s"WorkloadParsingException: can't find workload type for given input $s")
    }) match {
      case s: JsSuccess[CoreJob] => s.get
      case s: JsSuccess[StalenessJob] => s.get
      case e: JsError => println("Error: " + JsError.toJson(e).toString())
        throw new JsResultException(e.errors)
    }
  }

  def exportFolder(job: Job): String = {
    val s = job.jobID.split("-")
    val folder = if (job.batchname != "") {
      "results/" + job.batchname + "/"
    }
    else {
      "results/"
    }
    if (s.length > 1) {
      folder + DateUtil.formatTimeStamp(s.head) + "-" + s.tail.reduce(_ + "-" + _)
    } else folder + DateUtil.formatTimeStamp(s.head)
  }
}

abstract class Counts {
  // The number of records already in the table before the transactional phase (load phase see insert).
  def recordcount: Int
  // number of warmup operations (excluded from measurement)
  def warmupcount: Int
  // The number of operations to use during the run phase.
  def operationcount: Int
  // Indicates how many inserts to do
  // Could support the "insertstart" property, which tells them which record to start at.
  def insertcount: Int
  def insertstart: Int
}

abstract class Proportions

abstract class Distributions

abstract class LoadGeneration {
  // "deadline": method by YCSB; "adaptive": ... ToDo
  def schedulingmethod: String
  // distribution of time between requests ("exponential", "constant" (YCSB) or "gamma").
  // note that exponential think-time periods leads to a poisson distributed number of requests in a fixed interval of time.
  def interrequesttimedistribution: String
}