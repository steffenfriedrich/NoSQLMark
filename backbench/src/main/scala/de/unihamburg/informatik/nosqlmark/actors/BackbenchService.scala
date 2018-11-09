package de.unihamburg.informatik.nosqlmark.actors

import akka.actor.{Actor, Deploy, Props}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.{Cluster, Member}
import akka.event.Logging
import akka.remote.RemoteScope
import de.unihamburg.informatik.nosqlmark.api.CommonProtocol.StoppedJob
import de.unihamburg.informatik.nosqlmark.api._
import de.unihamburg.informatik.nosqlmark.measurements.{MeasurementsAggregator, StatusReporter}
import de.unihamburg.informatik.nosqlmark.protocols.{ClusterProtocol, MeasurementProtocol}
import de.unihamburg.informatik.nosqlmark.status.JobStatus._
import de.unihamburg.informatik.nosqlmark.status._
import scala.concurrent.duration._

object BackbenchService {
  def props(): Props =
    Props(classOf[BackbenchService])
}

/**
  * Created by Steffen Friedrich on 09.05.2016.
  */
class BackbenchService extends Actor {
  val system = context.system
  import system.dispatcher
  val log = Logging(system.eventStream, "BackbenchService")

  val statusReporter = system.actorOf(StatusReporter.props())
  val measurementAggregator = system.actorOf(MeasurementsAggregator.props())

  // the public subscribe mediator is used to publish job results to subscribed clients
  val mediator = DistributedPubSub(system).mediator

  var master = Map[String, MasterState]()

  var clusterMember = Set[Member]()
  var jobQueue = JobStatus.empty

  // subscribe to cluster changes and re-subscribe when restart
  override def preStart(): Unit = Cluster(system).subscribe(self, initialStateMode = InitialStateAsEvents,
    classOf[MemberEvent], classOf[UnreachableMember])

  override def postStop(): Unit = Cluster(system).unsubscribe(self)

  def receive = {
    case CommonProtocol.Ping => sender() ! CommonProtocol.Pong

    case job: Job => {
      log.debug("received job " + job.jobID)
      if (jobQueue.isAccepted(job.jobID)) {
        sender() ! CommonProtocol.Recurrence(job.jobID)
      } else {
        log.debug("accepted job {}", job.jobID)
        jobQueue = jobQueue.update(JobAccepted(job))
        sender() ! CommonProtocol.Ack(job.jobID)
        proccessJobQueue()
      }
    }

    case ClusterProtocol.JobInitialized(masterID, jobID) => {
      master += (masterID -> MasterState(sender(), status = JobInitialized(jobID)))
      log.debug("registered master {} for job {}", masterID, jobID)

      val job = jobQueue.actualJob
      if (job.jobID == jobID && master.size == job.nodes &&
        (master forall {
          case (_, MasterState(ref, status: JobInitialized)) => true
          case _ => false
        })) {
        master.foreach(wm => {
          wm._2.ref ! ClusterProtocol.RunJob
          log.debug("command master {} to run job {}", wm._1, jobID)
          master += (wm._1 -> wm._2.copy(status = Benchmarking(jobID)))
          log.debug("changed state of {} from JobInitialized to Benchmarking({})", masterID, jobID)
        })
      }
    }


    case report: ReportingProtocol.StatusReport => statusReporter ! report


    case ClusterProtocol.JobIsDone(masterID, jobID, result) => {
      if (!jobQueue.isInProgress(jobID)) {
        log.warning("job {} is not in progress, reported as done by master {}", jobID, masterID)
      } else {
        log.debug("job {} is reported as done by master {}", jobID, masterID)

        master.get(masterID) match {
          case Some(s@MasterState(_, Benchmarking(jobID))) =>
            master += (masterID -> s.copy(status = Idle))
            log.debug("changed state of {} from Benchmarking to Idle", masterID)
          case _ => // not busy
        }

        sender() ! CommonProtocol.Ack(jobID)
        measurementAggregator ! MeasurementProtocol.Histograms(jobID, masterID, result)

        if (master forall {
          case (_, MasterState(ref, Idle)) => true
          case _ => false
        }) measurementAggregator ! MeasurementProtocol.FinishMeasurement(jobID, 0L)
      }
    }


    case CommonProtocol.StopJob(jobID) => {
      jobQueue = jobQueue.update(JobStopped(jobID))
      log.debug("stopped job {}", jobID)
      mediator ! Publish(jobID, StoppedJob(jobID))
      proccessJobQueue()
    }


    // ToDo failure handling (in client)
    case ClusterProtocol.JobFailed(masterID, jobID, throwable) => {
      log.error(throwable, "job " + jobID + " failed by master " + masterID)
      jobQueue = jobQueue.update(MasterFailed(jobID))
      proccessJobQueue()
    }


    case MeasurementProtocol.AggregatedMeasurements(jobID, aggregatedResult) => {
      jobQueue = jobQueue.update(JobCompleted(jobID))
      log.debug("job {} completed", jobID)
      mediator ! Publish(jobID, JobResult(jobID, aggregatedResult))
      measurementAggregator ! MeasurementProtocol.CleanUpMeasurements(jobID)
      statusReporter ! ReportingProtocol.CleanUpReport(jobID)
      proccessJobQueue()
    }


    // react on cluster membership changes
    case MemberUp(member) => {
      log.debug("Member with roles {} is Up: {}", member.roles, member.address)
      if (member.hasRole("backbench")) {
        clusterMember = clusterMember + member
        proccessJobQueue()
      }
    }


    case UnreachableMember(member) => {
      log.debug("Member detected as unreachable: {}", member)
    }


    case MemberRemoved(member, previousStatus) => {
      log.debug("Member is Removed: {} after {}",
        member.address, previousStatus)
      if (member.hasRole("backbench"))
        clusterMember = clusterMember - member
    }
  }


  def proccessJobQueue() = if (!jobQueue.inProgress && jobQueue.hasJob) {
    val job = jobQueue.nextJob
    jobQueue = jobQueue.update(JobStarted(job.jobID))

    // check if enough nodes are available
    if (job.nodes > clusterMember.size) {
      jobQueue = jobQueue.update(JobStopped(job.jobID))
      log.debug("only {} nodes are available," +
        s" but your desired nodes for job {} are {}, please change the parameter and retry", clusterMember.size, job.jobID, job.nodes)
      mediator ! Publish(job.jobID, JobFailure(job.jobID, s": only ${clusterMember.size} nodes are available," +
        s" but your desired nodes for job ${job.jobID} are ${job.nodes}, please change the parameter and retry"))
    } else {
      // stop all master of previous job
      stopAllmaster

      val props: Option[Props] = job match {
        case j: CoreJob => Some(CoreMaster.props(self))
        case j: StalenessJob => Some(StalenessMaster.props(self))
        case _ => {
          None
        }
      }

      if (props.isDefined) {
        statusReporter ! ReportingProtocol.StartReporting(job.jobID, job.nodes)
        val wfn = job.workForNodes
        var n = 0
        clusterMember.slice(0, job.nodes).foreach(cm => {
          val master = system.actorOf(props.get.withDeploy(Deploy(scope = RemoteScope(cm.address))))
          val work = wfn(n)
          n += 1
          master ! work
        })
        sender() ! CommonProtocol.Ack(job.jobID)
      } else {
        jobQueue = jobQueue.update(JobStopped(job.jobID))
        log.error("unknown job {} for {}", job.getClass, job.jobID)


        system.scheduler.scheduleOnce(1 seconds, mediator,
          Publish(job.jobID, JobFailure(job.jobID, s"unknown job type ${job.getClass}")))
      }
    }
  }


  def stopAllmaster: Unit = {
    master.foreach(master => {
      system.stop(master._2.ref)
    })
    master = Map[String, MasterState]()
  }


}
