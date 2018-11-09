package de.unihamburg.informatik.nosqlmark.actors
import akka.actor.{ActorSystem, AddressFromURIString, PoisonPill, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKitBase}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.unihamburg.informatik.nosqlmark.api.{CommonProtocol, CoreJob, JobResult}
import de.unihamburg.informatik.nosqlmark.protocols.ClusterProtocol.RegisterMaster
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Synchronous Unit Testing with TestActorRef
 * Created by Steffen Friedrich on 18.05.2015.
 */
class BackbenchServiceSpec extends TestKitBase  with WordSpecLike with MustMatchers {
  implicit lazy val system = ActorSystem()

  "A ClusterMaster actor" must {

    val backbenchRef = TestActorRef[BackbenchService](BackbenchService.props(),"backbench")

    "receive and accept a job" in {
      val job = new CoreJob(jobID = "job-001")

      implicit val timeout = Timeout(10 seconds)

      val future = backbenchRef ? job

      val Success(result: CommonProtocol.Ack) = future.value.get

      assert(result == CommonProtocol.Ack("job-001"))


      // assert(backbenchRef.underlyingActor.jobQueue.hasJob == true)

      // val acceptedJob = backbenchRef.underlyingActor.jobQueue.nextJob
      // assert(acceptedJob.jobID == job.jobID)
    }

    // ToDo more test cases
  }


}
