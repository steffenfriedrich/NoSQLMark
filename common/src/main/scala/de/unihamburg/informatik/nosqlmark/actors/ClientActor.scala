package de.unihamburg.informatik.nosqlmark.actors

import akka.actor.{Actor, ActorRef}
import akka.cluster.pubsub.DistributedPubSubMediator.{SubscribeAck, Subscribe}
import akka.cluster.pubsub.{DistributedPubSub}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import de.unihamburg.informatik.nosqlmark.api._
import de.unihamburg.informatik.nosqlmark.util.{DateUtil, MeasurementUtil}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Routes a benchmarking job to the ClusterMaster and uses a Future (?)
 * to hopefully retrieve an Ack. Otherwise (timeout, exception) it sends
 * NotOk back to the original sender.
 *
 * Created by Steffen Friedrich on 04.05.2015.
 */
class ClientActor extends Actor {

  import context.dispatcher

  val log = Logging(context.system.eventStream, "ClientActor")
  val clusterMasterProxy = context.system.actorOf(ClusterSingletonProxy.props(singletonManagerPath = "/user/backbench",
    settings = ClusterSingletonProxySettings(context.system).withRole("backbench")))

  val mediator = DistributedPubSub(context.system).mediator
  implicit val timeout = Timeout(10 seconds)

  // jobID -> original sender
  val clients = mutable.Map[String, ActorRef]()
  // jobID -> job
  val jobs = mutable.Map[String, Job]()
  // jobID -> jobResult
  val results = mutable.Map[String, JobResult]()


  def receive = {

    case CommonProtocol.Ping => {
      implicit val timeout = Timeout(10 seconds)
      (clusterMasterProxy ? CommonProtocol.Ping) map {
        case CommonProtocol.Pong => {
          println("Connected to BackbenchService " + clusterMasterProxy.path)
          CommonProtocol.Pong
        }
      } recover {
        case _ => {
          println("BackbenchService is not reachable, please check your nosqlmark.conf and edit the seed-nodes if needed.")
          //println("Your seed-nodes are: ")
          //context.system.settings.config.getList("akka.cluster.seed-nodes").unwrapped().toArray.foreach(x => println(x + ""))
          CommonProtocol.NotOk
        }
      } pipeTo sender()
    }

    case ReportingProtocol.StatusReport(jobID, reportIteration, throughput, estremaining, warmup,counts, mean, min, max) =>  {
      val timeString = DateUtil.formatSeconds(estremaining)

      warmup match {
        case true => println(s"Warm-up status for $jobID: $throughput ops/sec, completion of warmup in $timeString")
        case false => println(s"Status for $jobID for last $counts ops: $throughput ops/sec, latency [mean $mean, min $min, max $max] ms, est completion in $timeString")
      }
    }

    case job: Job => {
      clients.put(job.jobID, sender)
      jobs.put(job.jobID, job)
      mediator ! Subscribe(job.jobID, self)

      implicit val timeout = Timeout(2 seconds)
      (clusterMasterProxy ? job) map {

        case CommonProtocol.Ack(jobID) => {
          println("successfully delivered Job: " + jobID)
          CommonProtocol.Ack(jobID)
        }

        case CommonProtocol.Recurrence(jobID) => {
          println(s"job $jobID has already been delivered in the past.")
          CommonProtocol.Recurrence(jobID)
        }

        case CommonProtocol.NotOk(message) => {
          println(message)
          CommonProtocol.NotOk(message)
        }
      } recover {
        case _ => {
          println("wasn't able to deliver Job: " + job.jobID)
          CommonProtocol.NotOk
        }
      } pipeTo sender()
    }


    case JobResult(jobID, result) => {
      val job: Job = jobs.remove(jobID).get
      val originalsender: ActorRef = clients.remove(jobID).get
      result match {
        case result: Map[String, String] => {
          println("received result for job " + jobID)
          Job.export(job, Job.exportFolder(job) + "/workload.json")
          MeasurementUtil.exportMeasurementsToFiles(result, jobID, Job.exportFolder(job))
          originalsender ! JobResult(jobID, result)
        }
        case _ => println("received unknown result for job {}: {}", jobID, result)
      }
    }

    case JobFailure(jobID, message) => {
      println(s"received failure message for job ${jobID}: ${message}")
      jobs.remove(jobID).get
      val originalsender: ActorRef = clients.remove(jobID).get
      originalsender ! JobFailure(jobID, message)
    }

    case stopJob: CommonProtocol.StopJob => {
      implicit val timeout = Timeout(10 seconds)
      (clusterMasterProxy ? stopJob) map {
        case CommonProtocol.Ack(jobID) => {
          CommonProtocol.Ack(jobID)
        }
      } recover {
        case _ => {
          CommonProtocol.NotOk
        }
      } pipeTo sender()
    }

    case subscribeAck: SubscribeAck => // log.debug("subscribed for job {}", subscribeAck.subscribe.topic)

    case _ =>
  }
}