package de.unihamburg.informatik.nosqlmark.api

/**
 * Created by Friedrich on 21.05.2015.
 */
object CommonProtocol {

  case class Ack(jobID: String)

  case object NotOk
  case class NotOk(message: String)

  case class Recurrence(jobID: String)

  case object Ping
  case object Pong

  case class StopJob(jobID: String)
  case class StoppedJob(jobID: String)
}
