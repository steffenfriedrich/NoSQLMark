package de.unihamburg.informatik.nosqlmark.api

/**
 * Created by Steffen Friedrich on 21.05.2015.
 */
case class JobResult(jobId: String, result: Map[String, String])

case class JobFailure(jobId: String, message: String)
