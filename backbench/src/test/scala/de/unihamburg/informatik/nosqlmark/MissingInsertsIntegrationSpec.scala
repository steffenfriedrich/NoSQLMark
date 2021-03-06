package de.unihamburg.informatik.nosqlmark

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.unihamburg.informatik.nosqlmark.actors.{BackbenchService, ClientActor}
import de.unihamburg.informatik.nosqlmark.api._
import de.unihamburg.informatik.nosqlmark.util.{FlakeIDGen, NetworkUtil}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object MissingInsertsIntegrationSpec {
  val params = new Parameters(Seq[String]())
  val config = ConfigFactory.parseFile(new File(params.configFile())).withFallback(ConfigFactory.load())
}

/**
  * Created by Steffen Friedrich on 25.05.2015.
  */
class MissingInsertsIntegrationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("NoSQLMarkCluster", MissingInsertsIntegrationSpec.config.getConfig("client").withFallback(MissingInsertsIntegrationSpec.config)))

  val config = MyIntegrationSpec.config

  // start SickStore and connect to
  // ServerStartup.main(Array("./config/sickstore/config.yml"))

  // start actor systems
  val system1 = startBackbenchService()
  Thread.sleep(1000)
  val system2 = startBackbenchService(2553)
  Thread.sleep(3000)

  "In a mixed workload with inserts, the reads" must {
    "request only inserted keys" in {
      val client = system.actorOf(Props[ClientActor], "client")
      client ! CommonProtocol.Ping
      expectMsg(8 seconds, CommonProtocol.Pong)

      // load phase
      val loadJob: Job = CoreJob(
        workload = "CoreWorkload",
        //dbname = "SickStoreClient",
        dbname = "LocalSickStoreClient",
        batchname = "MissingInsertsIntegrationSpec",
        worker = 1,
        nodes = 1,
        phase = "load",
        asyncmode = true,
        counts = CoreCounts(
          recordcount = 300000,
          operationcount = 300000,
          insertcount = 300000,
          warmupcount = 0
        ),
        loadgeneration = CoreLoadGeneration(
          interrequesttimedistribution = "constant"
        ),
        target = 3000,
        dbproperties = Map(
          "sickstore.host" -> "localhost",
          "sickstore.port" -> "54000",
          "sickstore.timeout" -> "1000",
          "sickstore.maxconnections" -> "1",
          "sickstore.write_concern.ack" -> "0",
          "sickstore.write_concern.journaling" -> "false",
          "sickstore.read_preference" -> "secondary",
          //"sickstore.localconfig" -> "./config/sickstore/config_no_delay_hickup.yml"
          //"sickstore.localconfig" -> "./config/sickstore/config_no_delay_hickup_2.yml"
          //"sickstore.localconfig" -> "./config/sickstore/config_no_delay_hickup_1000.yml"
          //"sickstore.localconfig" -> "./config/sickstore/hickup1000_max1053.yml"
          //"sickstore.localconfig" -> "./config/sickstore/hickup1000_max1666.yml"
          //"sickstore.localconfig" -> "./config/sickstore/hickup1000_max1111.yml"
          "sickstore.localconfig" -> "./config/sickstore/config.yml"
          //"sickstore.localconfig" -> "./config/sickstore/hickup1000_max10000.yml"
        ),
        logmeasurements = false,
        logjvmstats = false
      )
      client ! loadJob
      expectMsgType[CommonProtocol.Ack](80 seconds)
      expectMsgType[JobResult](1200 seconds)


      // transactional phase
      val mixedJob = loadJob.asInstanceOf[CoreJob].copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "transactional",
        target = 3000,
        proportions = CoreProportions(readproportion = 0.60,
          updateproportion = 0.0,
          insertproportion = 0.40,
          scanproportion = 0.0,
          readmodifywriteproportion = 0.0
        ) ,
        counts = CoreCounts(
          recordcount = 300000,
          operationcount = 300000
        )
      )
      client ! mixedJob
      expectMsgType[CommonProtocol.Ack](80 seconds)
      expectMsgType[JobResult](1200 seconds)
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system1)
    TestKit.shutdownActorSystem(system2)
    TestKit.shutdownActorSystem(system)
  }

  def startBackbenchService(port: Int = 2552) = {
    val system = ActorSystem("NoSQLMarkCluster",  ConfigFactory.parseString(
      s"""akka.remote.netty.tcp.port=${port}
        akka.cluster.roles = ["backbench"]
     """.stripMargin).withFallback(config.getConfig("backbench")).withFallback(config))

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = BackbenchService.props(),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withRole(Some("backbench"))),
      name = "backbench")

    // wait for clustermaster before workermaster system will be started
    implicit val timeout = Timeout(10, TimeUnit.SECONDS)
    val selection = system.actorSelection("/user/backbench")
    val actor = Await.result(selection.resolveOne(), timeout.duration)
    system
  }
}
