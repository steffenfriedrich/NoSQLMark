package de.unihamburg.informatik.nosqlmark.status

import de.unihamburg.informatik.nosqlmark.api.ReportingProtocol.StatusReport


/**
  * Created by Steffen Friedrich on 18.02.2016
  */
case class WorkStatus(jobID: String,
                      warmupOpsToDo: Int = 0,
                      opsToDo: Int = 0) {

  private var warmupOpsDone: Int = 0
  private var opsDone: Int = 0
  private var warmupStartTime: Long = 0
  private var warmupEndTime: Long = 0
  private var startTime: Long = 0

  private var scheduleTimeNanos: Long = 0
  private var warmingUp: Boolean = warmupOpsToDo > 0
  private var statusReportCounter = 0

  private def totalOpsToDo = opsToDo + warmupOpsToDo

  private def totalOpsDone = opsDone + warmupOpsDone

  def warmedUp = totalOpsDone >= warmupOpsToDo

  def getOpsToDo = if(warmedUp) opsToDo else warmupOpsToDo

  def getOpsDone = if(warmedUp) opsDone else warmupOpsDone

  def addOneOp = if(warmedUp) opsDone += 1
  else warmupOpsDone += 1

  def addOps(ops: Int) = {
    if(warmedUp) opsDone += ops
    else {
      if(warmupOpsDone + ops <= warmupOpsToDo) warmupOpsDone += ops
      else {
        val diff = warmupOpsDone + ops - warmupOpsToDo
        warmupOpsDone = warmupOpsToDo
        opsDone += diff
      }
    }
  }

  def setStartTime:Unit = setStartTime(System.nanoTime())

  def setStartTime(time: Long):Unit = {
    if(warmingUp) {
      warmupStartTime = time
    }
    else {
      startTime = time
    }
    scheduleTimeNanos = time
  }

  def getStartTime = if(warmedUp) startTime
  else warmupStartTime


  def checkWarmup: Boolean = {
    if(warmingUp && warmedUp) {
      val now = System.nanoTime()
      warmingUp = false
      startTime = now
      scheduleTimeNanos = now
      true
    } else false
  }

  def notFinished: Boolean = totalOpsDone < totalOpsToDo

  def getThroughput: Double = 1000000000.0 * getOpsDone / (System.nanoTime - getStartTime)

  def getEstremaining: Int = Math.ceil((getOpsToDo - getOpsDone) / getThroughput).toInt

  def setScheduleTimeNanos(now: Long) = scheduleTimeNanos = now

  def getScheduleTimeNanos: Long = scheduleTimeNanos

  def getStatusReport: StatusReport = {
    statusReportCounter += 1
    StatusReport(jobID = this.jobID,
      reportIteration = statusReportCounter,
      throughput = getThroughput,
      estremaining = getEstremaining,
      warmingUp = !warmedUp,
      counts = 0,
      mean = 0,
      min = 0,
      max = 0
    )
  }


}


