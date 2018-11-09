package de.unihamburg.informatik.nosqlmark.actors

import akka.actor._
import akka.event.Logging
import de.unihamburg.informatik.nosqlmark.measurements.MeasurementsActor
import de.unihamburg.informatik.nosqlmark.protocols.ClusterProtocol._
import de.unihamburg.informatik.nosqlmark.protocols.{ClusterProtocol, MeasurementProtocol, WorkerProtocol}
import de.unihamburg.informatik.nosqlmark.status._
import de.unihamburg.informatik.nosqlmark.util.{FlakeIDGen, NetworkUtil, Util}
import com.yahoo.ycsb.DB
import de.unihamburg.informatik.nosqlmark.api.{CommonProtocol, ReportingProtocol, StalenessJob}
import de.unihamburg.informatik.nosqlmark.db.NoSQLMarkDBFactory
import de.unihamburg.informatik.nosqlmark.workloads.StalenessWorkload

import scala.collection.mutable
import scala.concurrent.duration._

object StalenessMaster {
  def props(backbenchService: ActorRef): Props =
    Props(classOf[StalenessMaster], backbenchService)
}

/**
  * Created by Steffen Friedrich on 06.02.2017.
  */
class StalenessMaster(backbenchService: ActorRef) extends Actor {
  val masterID: String = "cm-" + FlakeIDGen.getSnowflakeIpIdString(NetworkUtil.ip)
  val log = Logging(context.system.eventStream, "Master")
  log.debug(" {} started by {} ", masterID, backbenchService)

  // they all need to be reset after job is done
  private var currentJob: Option[StalenessJob] = None
  private var currentResult: Option[Map[String, String]] = None
  private var currentDB: Option[DB] = None
  private var worker = mutable.Map[String, WorkerState]()
  private var measurementActor: Option[ActorRef] = None


  def receive = idle

  def idle: Receive = {

    case job: StalenessJob => {
      currentJob = Some(job)
      log.debug("{} got job " + job.jobID, masterID)
      try {
        val targetperworker = job.target / job.worker.toDouble
        val workloadHelper = new StalenessWorkload(job)
        var opCount: Int = 0
        opCount = if (job.phase == "transactional") job.counts.operationcount
        else if (job.counts.insertcount > 0 && job.counts.insertstart >= 0) job.counts.insertcount
        else job.counts.recordcount

        measurementActor = Some(context.actorOf(MeasurementsActor.props(job.copy(counts = job.counts.copy(operationcount = opCount)), masterID)))

        for (i <- 0 to (job.worker - 1)) {
          var workerOpCount = math.floor(opCount / job.worker).toInt
          var workerWarmup = math.floor(job.counts.warmupcount / job.worker).toInt
          if (i < opCount % job.worker) workerOpCount = workerOpCount + 1
          if (i < job.counts.warmupcount % job.worker) workerWarmup = workerWarmup + 1
          val workerID = masterID + "-" + i
          val work = job.copy(
            target = targetperworker,
            counts = job.counts.copy(
              operationcount = workerOpCount,
              insertcount = workerOpCount,
              warmupcount = workerWarmup
            ))
          val dbproperties = Util.map2Properties(job.dbproperties + ("columnfamily" -> job.columnfamily))
          currentDB = Some(NoSQLMarkDBFactory.newDB(job.dbname, dbproperties))
          worker += (workerID -> new WorkerState(context.actorOf(
            StalenessWorker.props(workerID, currentDB.get, work, workloadHelper, measurementActor.get)), Started))
        }
        worker.foreach(w => w._2.ref ! WorkerProtocol.Initialize)
        context.become(benchmarking)
      }
      catch {
        // let the cluster master handle the exception and become idle again
        case e: Exception => backbenchService ! JobFailed(masterID, job.jobID, e)
          cleanUp
          context.become(idle)
      }
    }
  }


  def benchmarking: Receive = {
    case WorkerProtocol.Ready(workerID) =>
      changeWorkerToWorkInitialized(workerID)
      if ((worker forall {
        case (_, WorkerState(ref, WorkInitialized)) => true
        case _ => false
      })) {
        log.debug("{} every worker is ready", masterID)
        backbenchService ! ClusterProtocol.JobInitialized(masterID, currentJob.get.jobID)
      }

    case ClusterProtocol.RunJob => {
      val startTime = System.nanoTime()
      measurementActor.get ! MeasurementProtocol.StartMeasurement(currentJob.get.jobID, startTime)
      worker foreach {
        w => {
          changeWorkerToWorking(w._1, currentJob.get.jobID)
          w._2.ref ! WorkerProtocol.Run
        }
      }
    }

    case report: ReportingProtocol.StatusReport => backbenchService ! report

    case failure: ClusterProtocol.JobFailed => {
      backbenchService ! failure.copy(masterID)
    }

    case WorkerProtocol.WorkIsDone(workerID, jobID) => {
      changeWorkerToWorkIsDone(workerID, jobID)
      if ((worker forall {
        case (_, WorkerState(ref, WorkIsDone(_))) => true
        case _ => false
      })) {
        val endTime = System.nanoTime()
        measurementActor.get ! MeasurementProtocol.FinishMeasurement(jobID, endTime)
        tryToFinishWork(jobID)
      }
    }

    case MeasurementProtocol.Histograms(jobID, wm, histograms) => {
      currentResult = Some(histograms)
      tryToFinishWork(jobID)
    }

    case WorkerProtocol.WorkerFailed(workerID, jobID, throwable) =>
      log.error(throwable, "currentJob " + jobID + " failed by Worker " + workerID)


    case stopJob: CommonProtocol.StopJob => {
      // ToDo stop job?
      log.debug("{} received request to stop the job {}", masterID, stopJob.jobID)
    }

    case _ => log.debug("{} received unknown message during benchmarking", masterID)
  }


  def tryToFinishWork(jobID: String) = {
    log.debug("{}: try to finish job {}", masterID, jobID)
    if ((worker forall {
      case (_, WorkerState(ref, WorkIsDone(_))) => true
      case _ => false
    }) && currentResult.isDefined) {
      backbenchService ! ClusterProtocol.JobIsDone(masterID, currentJob.get.jobID, currentResult.get)
      context.setReceiveTimeout(2.minutes)
      context.become(waitForJobIsDoneAck)
    }
  }

  def waitForJobIsDoneAck: Receive = {
    case CommonProtocol.Ack(id) if id == currentJob.get.jobID => {
      cleanUp
      backbenchService ! MasterRequestsJob(masterID)
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)
    }
    case ReceiveTimeout =>
      log.debug("{}: no job is done ack from ClusterMaster, retrying", masterID)
      backbenchService ! JobIsDone(masterID, currentJob.get.jobID, currentResult.get)
  }

  def changeWorkerToWorkInitialized(workerID: String) = {
    worker.get(workerID) match {
      case Some(s@WorkerState(_, Started)) =>
        worker += (workerID -> s.copy(status = WorkInitialized))
        log.debug("changed state of {} from Started to WorkInitialized", workerID)
      case _ => // not busy
    }
  }

  def changeWorkerToWorking(workerID: String, jobID: String) = {
    worker.get(workerID) match {
      case Some(s@WorkerState(_, WorkInitialized)) =>
        worker += (workerID -> s.copy(status = Working(jobID)))
        log.debug("changed state of {} from WorkInitialized to Working", workerID)
      case _ => // not busy
    }
  }

  def changeWorkerToWorkIsDone(workerID: String, jobID: String) = {
    worker.get(workerID) match {
      case Some(s@WorkerState(_, Working(jobID))) =>
        worker += (workerID -> s.copy(status = WorkIsDone(jobID)))
        log.debug("changed state of {} from Working to WorkIsDone", workerID)
      case _ => // not busy
    }
  }


  def cleanUp = {
    currentDB.get.cleanup()
    currentDB = None
    currentJob = None
    worker.foreach(workerState => {
      context.stop(workerState._2.ref)
    })
    context.stop(measurementActor.get)
    worker.clear
    measurementActor = None
    currentResult = None
  }

  override def postRestart(reason: Throwable): Unit = {
    log.error(reason, " {} restarted", masterID)
    preStart()
  }

  override def postStop(): Unit = {
    log.debug(" {} will be stopped", masterID)
  }
}
