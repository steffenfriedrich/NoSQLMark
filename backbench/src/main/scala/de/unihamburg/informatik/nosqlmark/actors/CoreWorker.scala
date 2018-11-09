package de.unihamburg.informatik.nosqlmark.actors

import java.util.concurrent.locks.LockSupport

import akka.actor._
import akka.event.Logging
import akka.pattern.after
import com.yahoo.ycsb.{DB, DBException, Status, WorkloadException}
import de.unihamburg.informatik.nosqlmark.api.CoreJob
import de.unihamburg.informatik.nosqlmark.protocols.WorkerProtocol.DoOperation
import de.unihamburg.informatik.nosqlmark.protocols._
import de.unihamburg.informatik.nosqlmark.status.WorkStatus
import de.unihamburg.informatik.nosqlmark.workloads._

import scala.concurrent.{Future, blocking}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.pattern.after

import scala.concurrent.duration._
import com.yahoo.ycsb.Status
import de.unihamburg.informatik.nosqlmark.concurrent.IOExecutionContext
import de.unihamburg.informatik.nosqlmark.generator._
import org.apache.hadoop.mapreduce.v2.app.speculate.ExponentiallySmoothedTaskRuntimeEstimator

object CoreWorker {
  def props(workerID: String, db: DB, work: CoreJob, workloadHelper: CoreWorkload, measurementActor: ActorRef): Props =
   if(work.asyncmode) Props(classOf[CoreWorker], workerID, db, work, workloadHelper, measurementActor)
   // use  pinned-dispatcher for synchronous benchmarking
   else Props(classOf[CoreWorker], workerID, db, work, workloadHelper, measurementActor).withDispatcher("pinned-dispatcher")
}

/**
 * Created by Steffen Friedrich on 04.06.2015.
 */
class CoreWorker(val workerID: String, db: DB, work: CoreJob, workload: CoreWorkload, measurementActor: ActorRef) extends Actor {
  val log = Logging(context.system.eventStream, "Worker")
  val logTimeseries = Logging(context.system.eventStream, "Timeseries")

  val master = this.context.parent

  val interRequestTimeGen: InterRequestTimeGenerator = work.loadgeneration.interrequesttimedistribution match {
    case "exponential" => new ExponentialInterRequestTimeGenerator(work.target)
    case "gamma" => new GammaInterRequestTimeGenerator(work.target, 0.5) // ToDo add shape parameter to Job
    case "lognormal" => new LognormalInterRequestTimeGenerator(work.target, 2.3)
    case _ => new ConstantInterRequestTimeGenerator(work.target)
  }

  val timeout: Int = 5000
  val TIMEDOUT = new Status("TIMEDOUT", "The operation timedout");

  val measurementStatus = WorkStatus(
    work.jobID,
    warmupOpsToDo = work.counts.warmupcount,
    opsToDo = work.counts.operationcount
  )

  override def postStop() {
    log.debug("{} will be stopped", workerID)
  }

  def receive = init

  def init: Receive = {
    case WorkerProtocol.Initialize =>
      try {
        db.init()
        master ! WorkerProtocol.Ready(workerID)
        context.become(benchmarking)
      }
      catch {
        case e: DBException => master ! WorkerProtocol.WorkerFailed(workerID, work.jobID, e)
      }
  }

  def benchmarking: Receive = {
    case WorkerProtocol.Run => {
      if (measurementStatus.notFinished) {
        log.debug("{} start with benchmarking (warmupcount: {}, operationcount: {})", workerID, work.counts.warmupcount, work.counts.operationcount)

        // spread the thread operations out so they don't all hit the DB at the same time
        // if ((interRequestTimeGen.targetOpsPerMs > 0) && (interRequestTimeGen.targetOpsPerMs <= 1.0)) {
        //  val randomMinorDelay = ThreadLocalRandom.current.nextInt(interRequestTimeGen.targetOpsTickNs.toInt)
        //  sleepUntil(System.nanoTime() + randomMinorDelay)
        //}
        work.phase match {
          case "load" => self ! WorkerProtocol.DoInsert
          case "transactional" => self ! WorkerProtocol.DoTransaction
          case "delete" => self ! WorkerProtocol.DoDelete
        }
        measurementStatus.setStartTime
      } else {
        master ! WorkerProtocol.WorkerFailed(workerID, work.jobID,
          new WorkloadException("No operations to do , worker" + workerID))
      }
    }

    case WorkerProtocol.DoInsert => nextOperation(WorkerProtocol.DoInsert)

    case WorkerProtocol.DoDelete => nextOperation(WorkerProtocol.DoDelete )

    case WorkerProtocol.DoTransaction => nextOperation(WorkerProtocol.DoTransaction)

    case _ => log.warning("{} received unknown message.", workerID)
  }

  private def nextOperation(op: WorkerProtocol.DoOperation) = {
    val startTime = System.nanoTime()
    measurementStatus.checkWarmup
    val operation = op match {
      case WorkerProtocol.DoInsert => workload.nextInsertOperation
      case WorkerProtocol.DoDelete => workload.nextDeleteOperation
      case WorkerProtocol.DoTransaction => workload.nextOperation
    }
    if (work.asyncmode) {
      doAsyncOperation(startTime, operation, measurementStatus.warmedUp)
    }
    else {
      doSyncOperation(startTime, operation, measurementStatus.warmedUp)
    }
    measurementStatus.addOneOp
    throttleNanos()
    if (measurementStatus.notFinished) {
      self ! op
    }
    else {
      master ! WorkerProtocol.WorkIsDone(workerID, work.jobID)
    }
  }

  // import context.dispatcher // default akka ForkJoinPool => not good for blocking I/O
  implicit val executionContext = context.system.dispatchers.lookup("blocking-io-dispatcher")
  // import IOExecutionContext.ioThreadPool
  private def doAsyncOperation(startTime: Long, op: CoreOperation, warmedUp: Boolean): Unit = {
    val f = Future {
      // blocking construct is no longer needed, since we use a fixed thread pool
      blocking {
        doOperation(op, warmedUp, startTime)
      }
    }

    val t = after(duration = timeout milliseconds, using = context.system.scheduler)(
      Future.successful(MeasurementObject(startTime, op.toString, TIMEDOUT, warmedUp))
    )

    val future = Future firstCompletedOf Seq(f, t)

    future.onComplete {
      case Success(measurementObject) => {
        measureLatency(measurementObject)
      }
      case Failure(ex) => {
        ex.printStackTrace()
        log.error(ex, "Error occured during operation {}",op)
      }
    }
  }

  private def doSyncOperation(startTime: Long, op: CoreOperation, warmedUp: Boolean) =
    measureLatency(doOperation(op, warmedUp, startTime))


  private def doOperation(op: CoreOperation, warmedUp: Boolean, startTime: Long): MeasurementObject = {
    val status: Status = op match {
      case Cleanup => {
        db.cleanup
        Status.OK
      }
      case Read(table, key, fields, result) => db.read(table, key, fields, result)
      case Scan(table, startkey, recordcount, fields, result) => db.scan(table, startkey, recordcount, fields, result)
      case Update(table, key, values) => db.update(table, key, values)
      case Insert(table, key, values) => db.insert(table, key, values)
      case Delete(table, key) => db.delete(table, key)
      case ReadModifyWrite(table, key, fields, result, values) => {
        db.read(table, key, fields, result)
        db.update(table, key, values)
      }
    }
    MeasurementObject(startTime, op.toString, status, warmedUp)
  }

  val nanoToMillis = 1000000d
  private def measureLatency(measurementObject: MeasurementObject) = {
    if (measurementObject.warmedUp) {
      val starttime = measurementObject.startTime
      val endtime = System.nanoTime
      val operation = measurementObject.operation
      val result = measurementObject.result

      val latency = (endtime - starttime) / 1000
      if (work.logmeasurements) {
        logTimeseries.debug("{};{};{}", (starttime - measurementStatus.getStartTime) / nanoToMillis, measurementObject.operation, latency / 1000d)
      }
      result match {
        case Status.OK => {
          measurementActor ! MeasurementProtocol.Measure(work.jobID, operation, latency.toInt)
          measurementActor ! MeasurementProtocol.Measure(work.jobID, "ALL", latency.toInt)
        }
        case TIMEDOUT => {
          measurementActor ! MeasurementProtocol.Measure(work.jobID, operation+ "-TIMEDOUT", latency.toInt)
          measurementActor ! MeasurementProtocol.Measure(work.jobID, "ALL", latency.toInt)
        }
        case _ => {
          measurementActor ! MeasurementProtocol.Measure(work.jobID, operation+ "-FAILED", latency.toInt)
          measurementActor ! MeasurementProtocol.Measure(work.jobID, "ALL", latency.toInt)
        }
      }
    }
  }

  var deadline = 0L
  private def throttleNanos() {
    if (interRequestTimeGen.targetOpsPerMs > 0) {
      deadline = if(deadline == 0L) { measurementStatus.getStartTime + interRequestTimeGen.nextValue }
      else { deadline + interRequestTimeGen.nextValue }
      sleepUntil(deadline)
    }
  }

  private def sleepUntil(deadline: Long) {
    var now: Long = System.nanoTime
    scheduleReport(now)
    while (now < deadline) {
      now = System.nanoTime
      LockSupport.parkNanos(deadline - now)
    }
  }

  def scheduleReport(now: Long) = if((now - measurementStatus.getScheduleTimeNanos) >= 10000000000L) report(now)

  def report(now: Long) = {
    measurementStatus.setScheduleTimeNanos(now)
    if (measurementStatus.notFinished)
      measurementActor ! measurementStatus.getStatusReport
  }
}

