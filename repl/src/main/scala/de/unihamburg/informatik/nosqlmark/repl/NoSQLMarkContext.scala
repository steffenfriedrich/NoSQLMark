package de.unihamburg.informatik.nosqlmark.repl

import java.io.{File => JFile}

import akka.actor.{ActorSystem, PoisonPill, Props, _}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.{Config, ConfigFactory}
import de.unihamburg.informatik.nosqlmark.actors.{BackbenchService, ClientActor}
import de.unihamburg.informatik.nosqlmark.api.{CommonProtocol, CoreJob, Job}
import de.unihamburg.informatik.nosqlmark.util._

import scala.collection.immutable.TreeSet

/**
  * Created by Steffen Friedrich on 02.02.2016.
  */
class NoSQLMarkContext() {
  val configFile = if (new JFile("./config/nosqlmark.conf").isFile)
    new JFile("./config/nosqlmark.conf")
  else new JFile("../config/nosqlmark.conf")

  val config: Config = ConfigFactory.parseFile(configFile).withFallback(ConfigFactory.load())
  val system = ActorSystem("NoSQLMarkCluster", config.getConfig("client").withFallback(config))
  val client = system.actorOf(Props[ClientActor], "client")
  client ! CommonProtocol.Ping

  // visible for repl
  var Util = de.unihamburg.informatik.nosqlmark.util.Util

  def help = {
    println(
      """To create a benchmnarking job with default parameter:
        |  val jobA = CoreJob()
        |
        |Load a workload:
        |  val jobB = CoreJob.load("workloads/exported/sickstore_load.json")
        |
        |modify parameter:
        |  val jobC = CoreJob.update(jobB, "{
        |  \"jobID\": \"Test-001\",
        |  \"batchname\": \"2016_05_17_autoscale\"
        |  }")
        |
        |
        |To generate data for a throughput / latency plot given a batchname for coressponding jobs:
        |  - Export.genBatchPlotData(batchName: String)
        |
        """.stripMargin
    )
  }


  def submitJob(job: Job) = {
    client ! job
  }

  //def parseYCSBWorkloard(filePath: String): Job = YCSBWorkloadParser.parse(Array("--workloadfile", filePath))


  def autoScaleTarget(job: CoreJob, minThroughput: Double, maxThroughput: Double, runtimeInSec: Int): Unit =
    autoScaleTarget(job, minThroughput, maxThroughput, runtimeInSec, runtimeInSec)


  def autoScaleTarget(job: CoreJob, minThroughput: Double, maxThroughput: Double, runtimeInSec: Int, warmuptimeInSec: Int): Unit = {
    val min = math.floor(math.log10(minThroughput)).toInt
    val max = math.floor(math.log10(maxThroughput)).toInt
    val diff = (max - min).toInt

    var targets: Set[Int] = TreeSet()
    var insert = 0
    for (i <- min to max) {
      val lastPowOfTen = scala.math.pow(10, i - 1)
      val powOfTen = scala.math.pow(10, i)
      val smallsteps = (diff == 0 || maxThroughput < powOfTen * 2)

      for (j <- 0 to 9) {
        val factor = if (smallsteps) (lastPowOfTen * j).toInt
        else (powOfTen * j).toInt

        val throughput = (powOfTen + factor).toInt
        targets = targets + throughput
      }
    }
    targets foreach (throughput => {
      val ops = throughput * runtimeInSec
      val warmupOps = throughput * warmuptimeInSec
      if (throughput >= minThroughput && throughput <= maxThroughput) {
        val newJob = job.copy(
          jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
          target = throughput,
          counts = job.counts.copy(
            insertcount = ops,
            insertstart = insert,
            operationcount = ops,
            warmupcount = warmupOps
          )
        )
        insert += (2 * (ops)) + 1
        client ! newJob
        Thread.sleep(200)
      }
    })
  }

  def autoScaleWorker(job: CoreJob, maxWorker: Int, runtimeInSec: Int): Unit = {
    var insert = 0
    for {
      i <- 1 to maxWorker
    } {
      val ops = (job.target * runtimeInSec).toInt
      val newJob = job.copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        worker = i,
        counts = job.counts.copy(
          operationcount = ops,
          warmupcount = ops
        )
      )
      insert += (2 * (ops)) + 1
      client ! newJob
      Thread.sleep(1000)
    }
  }

  def repeatJob(job: CoreJob, times: Int) = {
    for (i <- 1 to times) {
      client ! job
      Thread.sleep(200)
    }
  }


  def genPlotData(batchName: String) = {
    MeasurementUtil.genPlotData(batchName)
  }

  def genPercentileDistributions(batchname: String) = {
    MeasurementUtil.genPercentileDistributions(batchname)
  }

  def genPercentileDistributions(batchname: String, percentileTicksPerHalfDistance: Int) = {
    MeasurementUtil.genPercentileDistributions(batchname, percentileTicksPerHalfDistance)
  }

  /**
    *
    * @return Snowflake ID String
    */
  def genID: String = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip)

  private def startClusterMaster = {
    val system = ActorSystem("NoSQLMarkCluster", config.getConfig("backbench").withFallback(config))
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = BackbenchService.props(),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withRole(Some("backbench"))),
      name = "backbench")
  }

}


object NoSQLMarkContext {

}