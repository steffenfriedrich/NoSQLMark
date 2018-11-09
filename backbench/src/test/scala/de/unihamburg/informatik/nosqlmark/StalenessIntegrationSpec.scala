package de.unihamburg.informatik.nosqlmark

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.unihamburg.informatik.nosqlmark.actors.{BackbenchService, ClientActor, CoreMaster}
import de.unihamburg.informatik.nosqlmark.api._
import de.unihamburg.informatik.nosqlmark.util.{FlakeIDGen, NetworkUtil, Util}
import de.unihamburg.sickstore.ServerStartup
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object StalenessIntegrationSpec {
  val params = new Parameters(Seq[String]())
  val config = ConfigFactory.parseFile(new File(params.configFile())).withFallback(ConfigFactory.load())
}

/**
  * Created by Steffen Friedrich on 06.02.2017.
  */
class StalenessIntegrationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("NoSQLMarkCluster", StalenessIntegrationSpec.config.getConfig("client").withFallback(StalenessIntegrationSpec.config)))

  val config = StalenessIntegrationSpec.config

  // start SickStore and connect to
  ServerStartup.main(Array("./config/sickstore/config.yml"))

  // start actor systems
  val system1 = startBackbenchService()
  Thread.sleep(1000)
  val system2 = startBackbenchService(2553)
  Thread.sleep(3000)

  val load = true;
  val syncmode = false;
  val asyncmode = true;
  val delete = true;

  "Some benchmark jobs" must {
    "return results" in {
      val client = system.actorOf(Props[ClientActor], "client")
      client ! CommonProtocol.Ping
      expectMsg(8 seconds, CommonProtocol.Pong)

      // load phase
      val loadJob = StalenessJob(
        workload = "CoreWorkload",
        dbname = "SickStoreClient",
        //dbname = "LocalSickStoreClient",
        batchname = "MyIntegrationTest",
        worker = 1,
        nodes = 2,
        phase = "load",
        asyncmode = true,
        counts = StalenessCounts(
          recordcount = 60000,
          operationcount = 60000,
          insertcount = 60000,
          warmupcount = 0
        ),
        proportions = StalenessProportions(
          stalenessmeasurementfraction = 0.1
        ),
        target = 1000,
        dbproperties = Map(
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
          //"sickstore.localconfig" -> "./config/sickstore/config.yml"
          "sickstore.localconfig" -> "./config/sickstore/hickup1000_max1250.yml"
        ),
        logmeasurements = false,
        logjvmstats = false
      )

      // Job.export(loadJob,"workloads/exported/sickstore_load.json")
      if (load) {
        client ! loadJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }

      // transactional phase
      val transactionalJob = loadJob.copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "transactional",
        target = 1000
      )
      if (syncmode) {
        client ! transactionalJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }


      // transactional phase with asynchronusly measurement
      val transactionalAsyncJob = loadJob.copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "transactional",
        asyncmode = true,
        target = 1000
      )

      Job.export(transactionalAsyncJob,"workloads/exported/sickstore_transactional.json")
      if (asyncmode) {
        client ! transactionalAsyncJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }
      // deletion phase
      val deleteJob = loadJob.copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "delete"
      )
      Job.export(deleteJob,"workloads/exported/sickstore_delete.json")
      if (delete) {
        client ! deleteJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }
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
