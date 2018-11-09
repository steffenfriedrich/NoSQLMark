package de.unihamburg.informatik.nosqlmark.util

import java.nio.file.{Files, Paths}
import de.unihamburg.informatik.nosqlmark.api._
import org.rogach.scallop.ScallopConf

import scala.io.Source

/**
  * Command line parser for the NoSQLMark command line client.
  * Use the companion objects parse function to construct one JobParser which holds
  * all parameters from given arguments, workload file and default values.
  *
  * Created by Steffen Friedrich on 20.04.2015.
  */
case class YCSBWorkloadParser(arguments: Seq[String]) extends ScallopConf(arguments) {

  import YCSBWorkloadParser.escapeComma

  version("NoSQLMark Command Line Client 1.0")
  banner(
    """"Usage: sbt "project cli" "run [options]"
      |Options:
      | """.stripMargin)
  mainOptions = Seq(workload, workloadfile, phase, dbname, target, worker, operationcount, recordcount, readproportion, updateproportion, insertproportion, scanproportion, readmodifywriteproportion, stalenessfraction)

  val workload = opt[String](short = 'w', required = true, default = Some("CoreWorkload"), descr = "the name of the workload class to use")

  val workloadfile = opt[String](short = 'f', descr = "load properties from the given file", validate = (file =>
    Files.exists(Paths.get(file))))

  val batchname = opt[String](required = false, default = Some(""), descr = "")

  val phase = opt[String](default = Some("transactional"), descr = "Start the transactional, the load or the delete phase (transactional, load, delete)")

  val asyncmode = opt[String](default = Some("true"), descr = "asynchronously do operations to avoid coordinated omission")

  val dbname = opt[String](required = true, default = Some("com.yahoo.ycsb.db.SickStoreClient"), descr = "specify the name of the DB binding to use")

  val dbproperties = props[String]('D', descr = "some key-value pairs used for database binding properties (e.g. sickstore.port=54234)")

  val target = opt[Double](short = 't', default = Some(1000.0), descr = "attempt to do n operations per second (0 =  unlimited)")

  val nodes = opt[Int](short = 'n', default = Some(1), descr = "The number of nodes, that should be used for the benachmark")

  val worker = opt[Int](short = 'x', default = Some(6), descr = "The number of worker per node")

  val table = opt[String](default = Some("usertable"), descr = "The name of the table")

  val columnfamily = opt[String](default = Some("family"), descr = "The column family of fields (required by some databases)")

  val recordcount = opt[Int](default = Some(10000), descr = "number of records")

  val insertcount = opt[Int](default = Some(10000), descr = "Indicates how many inserts to do, if less than recordcount (and greater than 0). Useful for partitioning the load among multiple servers, if the client is the bottleneck. Additionally, workloads should support the insertstart property, which tells them which record to start at.")

  val insertstart = opt[Int](default = Some(0), descr = "See insertcount option")

  val operationcount = opt[Int](default = Some(10000), descr = "number of operations to perform")

  val warmupcount = opt[Int](default = Some(1000), descr = "number of warmup operations (excluded from measurement")

  val fieldcount = opt[Int](noshort = true, default = Some(10), descr = "the number of fields in a record")

  val fieldlength = opt[Int](noshort = true, default = Some(100), descr = "the size of each field")

  val readallfields = opt[String](noshort = true, default = Some("true"), descr = "should reads read all fields or just one")

  val writeallfields = opt[String](noshort = true, default = Some("false"), descr = "should updates and read/modify/writes update all fields (true) or just one (false)")

  val readproportion = opt[Double](noshort = true, default = Some(0.50), descr = "proportion of reads")

  val updateproportion = opt[Double](noshort = true, default = Some(0.50), descr = "proportion of updates")

  val insertproportion = opt[Double](noshort = true, default = Some(0.00), descr = "proportion of inserts")

  val scanproportion = opt[Double](noshort = true, default = Some(0.00), descr = "proportion of scans")

  val readmodifywriteproportion = opt[Double](noshort = true, default = Some(0.00), descr = "proportion read a record, modify it, write it back")

  val stalenessfraction = opt[Double](short = 's', default = Some(0.10), descr = "fraction of the write operations, whose staleness should be measured.")

  val insertorder = opt[String](noshort = true, default = Some("hashed"), descr = "should records be inserted in order by key (ordered), or in hashed order (hashed) (default: hashed)")

  val loadgeneratordistribution =  opt[String](noshort = true, default = Some("exponential"), descr = "distribution of think time between requests. note that exponential think-time periods leads to a poisson distributed number of requests in a fixed interval of time.")

  val requestdistribution = opt[String](noshort = true, default = Some("zipfian"), descr = "what distribution should be used to select the records to operate on – uniform, zipfian or latest")

  val maxscanlength = opt[Int](noshort = true, default = Some(1000), descr = "what is the maximum number of records to scan")

  val scanlengthdistribution = opt[String](noshort = true, default = Some("uniform"), descr = "what distribution should be used to choose the number of records to scan (between 1 and maxscanlength)")

  val fieldlengthdistribution = opt[String](noshort = true, default = Some("constant"), descr = "what distribution should be used to choose the length of a field. Options are \"uniform\", \"zipfian\" (favoring short records), \"constant\", and \"histogram\".")

  val fieldlengthhistogram = opt[String](noshort = true, default = Some("hist.txt"), descr = "If  fieldlengthdistribution = \"histogram\", then the histogram will be read from the filename specified in the \"fieldlengthhistogram\" property.")

  val hotspotdatafraction = opt[Double](noshort = true, default = Some(0.0), descr = "Percentage of data items that constitute the hot set")

  val hotspotopnfraction = opt[Double](noshort = true, default = Some(0.0), descr = "Percentage of operations that access the hot set")

  val exponentialpercentile = opt[Double](noshort = true, default = Some(95.0), descr = "If \"requestdistriution\" is \"exponential\": What percentage of the readings should be within the most recent exponential.frac portion of the dataset")

  val exponentialfrac = opt[Double](noshort = true, default = Some(0.8571428571), descr = "If \"requestdistriution\" is \"exponential\": What fraction of the dataset should be accessed exponential.percentile of the time?")

  val logmeasurements = opt[String](default = Some("false"), descr = "Should log each latency measurement")

  errorMessageHandler = { message =>
    if (System.console() == null) {
      // no colors on output
      println("[%s] Error: %s" format(printedName, message))
    } else {
      println("[\u001b[31m%s\u001b[0m] Error: %s" format(printedName, message))
    }
  }

  /**
    * Build a [[CoreJob]]
    *
    * @param jobID
    * @return [[CoreJob]]
    */
  def getJob(jobID: String): CoreJob = {
    CoreJob(jobID,
      batchname(),
      workload(),
      dbname(),
      dbproperties.map(a => a._1 -> a._2.replaceAll(escapeComma,",")),
      target(),
      nodes(),
      worker(),
      table(),
      columnfamily(),
      phase(),
      asyncmode().toBoolean,
      CoreCounts(
        recordcount(),
        warmupcount(),
        operationcount(),
        insertcount(),
        insertstart(),
        fieldcount(),
        fieldlength(),
        readallfields().toBoolean,
        writeallfields().toBoolean
      ),
      CoreProportions(
        readproportion(),
        updateproportion(),
        insertproportion(),
        scanproportion(),
        readmodifywriteproportion()
      ),
      CoreDistributions(
        requestdistribution(),
        insertorder(),
        fieldlengthdistribution(),
        fieldlengthhistogram(),
        scanlengthdistribution(),
        maxscanlength(),
        hotspotdatafraction(),
        hotspotopnfraction(),
        exponentialpercentile(),
        exponentialfrac()
      ),
      CoreLoadGeneration(
        loadgeneratordistribution()
      ),
      logmeasurements().toBoolean
    )
  }
}

/**
  * Companion object that provides a parse function
  */
object YCSBWorkloadParser {
  private val escapeComma = "\u8704"

  def parse(args: Array[String]): CoreJob = {
    val allArgs = constructAllArgs(args)
    YCSBWorkloadParser(allArgs).getJob(FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip))
  }

  def load(filePath: String): CoreJob = YCSBWorkloadParser.parse(Array("--workloadfile", filePath))

  def help = YCSBWorkloadParser(Array("--help"))

  // constructs all args from given args and workload file if given
  private def constructAllArgs(args: Array[String]): Array[String] = {
    val conf = new YCSBWorkloadParser(args.map(s=>s.replaceAll(",", escapeComma)))
    val givenArgs = conf.summary.split("\n").filter(_.contains("*")).map(line => {
      line.splitAt(3)._2.split("=>")(0).trim()
    }).toList

    val fileArgs: Array[String] = if (!conf.workloadfile.isEmpty) {
      val file = Source.fromFile(conf.workloadfile()).getLines
      val newArgs = file.toList.filter(line => {
        line.contains("=") && (givenArgs.forall(arg => !line.contains(arg)))
      })
      newArgs.flatMap(line => {
        val a = line.split("=")
        a(0) match {
          case "dbproperties" => {
            val newline = line.replaceFirst("dbproperties=", "")
            val dbproperties = newline.split(" +").map(prop => prop.replaceAll(",", escapeComma))
            Array("-D" + dbproperties.head) ++ dbproperties.tail
          }
          case "exportfolder" => Array[String]()
          case "exportFolder" => Array[String]()
          case "exportfile" => Array[String]()
          case "histogrampath" => Array[String]()
          case "hdrhistogramoutputpath" => Array[String]()
          case "hdrhistogramfileoutput" => Array[String]()
          case _ => Array("--" + a(0), a(1))
        }
      }).toArray
    } else Array()
    conf.arguments.toArray ++ fileArgs
  }
}

