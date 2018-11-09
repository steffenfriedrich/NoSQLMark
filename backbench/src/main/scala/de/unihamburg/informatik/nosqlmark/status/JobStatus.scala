package de.unihamburg.informatik.nosqlmark.status

import de.unihamburg.informatik.nosqlmark.api.{CoreJob, Job}

import scala.collection.immutable.Queue

/**
 * Created by Steffen Friedrich on 11.05.2015.
 */
object JobStatus {
  def empty: JobStatus = JobStatus(
    pendingJob = Queue.empty,
    jobInProgress = Map.empty,
    acceptedJobIDs = Set.empty,
    doneJobIDs = Set.empty)

  trait JobDomainEvent
  case class JobAccepted(job: Job) extends JobDomainEvent
  case class JobStarted(jobID: String) extends JobDomainEvent
  case class JobCompleted(jobID: String) extends JobDomainEvent
  case class JobStopped(jobID: String) extends JobDomainEvent
  case class MasterFailed(jobID: String) extends JobDomainEvent
  case class MasterTimeout(jobID: String) extends JobDomainEvent

}

/**
 *
 * @param pendingJob
 * @param jobInProgress
 * @param acceptedJobIDs
 * @param doneJobIDs
 */
case class JobStatus(private val pendingJob: Queue[Job],
                     private val jobInProgress: Map[String, Job],
                     private val acceptedJobIDs: Set[String],
                     private val doneJobIDs: Set[String]) {

  import JobStatus._

  def hasJob: Boolean = pendingJob.nonEmpty
  def nextJob: Job = pendingJob.head
  def isAccepted(jobID: String): Boolean = acceptedJobIDs.contains(jobID)
  def isInProgress(jobID: String): Boolean = jobInProgress.contains(jobID)
  def isDone(jobID: String): Boolean = doneJobIDs.contains(jobID)
  def inProgress: Boolean = !jobInProgress.isEmpty
  def actualJob: Job = jobInProgress.head._2

  def update(event: JobDomainEvent): JobStatus = event match {
    case JobAccepted(job) => copy(
      pendingJob = pendingJob enqueue job,
      acceptedJobIDs = acceptedJobIDs + job.jobID)

    case JobStarted(jobID) =>
      val (job, rest) = pendingJob.dequeue
      require(jobID == job.jobID, s"JobStarted expected jobID $jobID == ${job.jobID}")
      copy(
        pendingJob = rest,
        jobInProgress = jobInProgress + (jobID -> job))

    case JobCompleted(jobID) => copy(
      jobInProgress = jobInProgress - jobID,
      doneJobIDs = doneJobIDs + jobID)

    //  todo what should happen during failure?
    case MasterFailed(jobID) => copy(
      jobInProgress = jobInProgress - jobID)

    case MasterTimeout(jobID) => copy(
      jobInProgress = jobInProgress - jobID)

    case JobStopped(jobID) => copy(
        jobInProgress = jobInProgress - jobID)

  }

}

