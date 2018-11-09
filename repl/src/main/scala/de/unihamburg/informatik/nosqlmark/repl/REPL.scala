package de.unihamburg.informatik.nosqlmark.repl

import de.unihamburg.informatik.nosqlmark.status.JobStatus

import scala.tools.jline_embedded.console.Operation
import scala.tools.nsc.Settings

/**
 * Created by Steffen Friedrich on 04.05.2015.
 */
object REPL {
  private var _interp: NoSQLMarkILoop = _

  def interp = _interp

  def interp_=(i: NoSQLMarkILoop) { _interp = i }

  def main(args: Array[String]) {
    val s = new Settings()

    s.embeddedDefaults[JobStatus]
    s.usejavacp.value = true
    //s.Xnojline.value  = true
    //s.processArguments(List("-usejavacp"), true)
    _interp = new NoSQLMarkILoop()
    _interp.process(s)

    println("Good bye ...")
    System.exit(1)
  }
}