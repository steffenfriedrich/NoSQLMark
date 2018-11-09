package de.unihamburg.informatik.nosqlmark.db

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.unihamburg.informatik.nosqlmark.Parameters
import de.unihamburg.informatik.nosqlmark.actors.{BackbenchService, ClientActor}
import de.unihamburg.informatik.nosqlmark.api._
import de.unihamburg.informatik.nosqlmark.util.{FlakeIDGen, NetworkUtil}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object RedisIntegrationSpec {
  val params = new Parameters(Seq[String]())
  val config = ConfigFactory.parseFile(new File(params.configFile())).withFallback(ConfigFactory.load())
}

/**
  * Created by Steffen Friedrich on 25.05.2015.
  */
class RedisIntegrationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("NoSQLMarkCluster", RedisIntegrationSpec.config.getConfig("client").withFallback(RedisIntegrationSpec.config)))

  val config = RedisIntegrationSpec.config

  // start actor systems
  val system1 = startBackbenchService()
  Thread.sleep(1000)
  val system2 = startBackbenchService(2553)
  Thread.sleep(3000)

  val load = false;
  val syncmode = false;
  val asyncmode = true;
  val delete = false;

  "Some benchmark jobs" must {
    "return results" in {
      val client = system.actorOf(Props[ClientActor], "client")
      client ! CommonProtocol.Ping
      expectMsg(8 seconds, CommonProtocol.Pong)

      // load phase
      val loadJob = CoreJob(
        workload = "CoreWorkload",
        dbname = "RedisClient",
        batchname = "RedisIntegrationSpec",
        worker = 1,
        nodes = 1,
        phase = "load",
        asyncmode = true,
        counts = CoreCounts(
          recordcount = 600000,
          operationcount = 600000,
          insertcount = 600000,
          warmupcount = 120000
        ),
        loadgeneration = CoreLoadGeneration(
          interrequesttimedistribution = "constant",
          schedulingmethod = "adaptive"
        ),
        target = 1000,
        dbproperties = Map(
          "redis.host" -> "134.100.11.133"
        ),
        logmeasurements = true,
        logjvmstats = false
      )

      Job.export(loadJob,"workloads/exported/redis_load.json")
      if (load) {
        client ! loadJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }

      // transactional phase
      val transactionalJob = loadJob.copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "transactional",
        target = 5000
      )
      if (syncmode) {
        client ! transactionalJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }


      // transactional phase with asynchronusly measurement
      val transactionalAsyncJob = loadJob.asInstanceOf[CoreJob].copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "transactional",
        asyncmode = true,
        counts = CoreCounts(
          recordcount = 600000,
          operationcount = 18000000,
          insertcount = 600000,
          warmupcount = 720000
        ),
        target = 3000
      )

      Job.export(transactionalAsyncJob,"workloads/exported/redis__transactional.json")
      if (asyncmode) {
        client ! transactionalAsyncJob
        expectMsgType[CommonProtocol.Ack](80 seconds)
        expectMsgType[JobResult](1200 seconds)
      }
      // deletion phase
      val deleteJob = loadJob.asInstanceOf[CoreJob].copy(
        jobID = FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip),
        phase = "delete"
      )
      Job.export(deleteJob,"workloads/exported/redis__delete.json")
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
