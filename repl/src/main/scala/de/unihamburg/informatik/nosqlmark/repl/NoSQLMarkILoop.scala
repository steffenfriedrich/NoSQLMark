package de.unihamburg.informatik.nosqlmark.repl

import java.io.BufferedReader

import scala.Predef.{println => _, _}
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._
import scala.tools.nsc.util._
import scala.util.Properties.{javaVersion, javaVmName, versionString}

class NoSQLMarkILoop(in0: Option[BufferedReader], out0: JPrintWriter) extends ILoop(in0, out0) {
  def this(in0: BufferedReader, out: JPrintWriter) = this(Some(in0), out)
  def this() = this(None, new JPrintWriter(Console.out, true))

  // Must be public for visibility
  var nosqlmarkContext: NoSQLMarkContext = _

  override def printWelcome() {
    echo("""Welcome to
        |_____   __     ____________________
        |___  | / /_______  ___/_  __ \__  /
        |__   |/ /_  __ \____ \_  / / /_  /
        |_  /|  / / /_/ /___/ // /_/ /_  /___
        |/_/ |_/  \____//____/ \___\_\/_____/
        |     ______  ___             ______
        |     ___   |/  /_____ __________  /__
        |     __  /|_/ /_  __ `/_  ___/_  //_/
        |     _  /  / / / /_/ /_  /   _  ,<
        |     /_/  /_/  \__,_/ /_/    /_/|_|
      """.stripMargin)


    val welcomeMsg = "Using Scala %s (%s, Java %s)".format(
      versionString, javaVmName, javaVersion)
    echo(welcomeMsg)
    echo("Type in expressions to have them evaluated.")
    echo("NoSQLMark context available as nc")
    echo("Type nc.help for more information.")
  }


  private val initialCommands = """
        @transient val nc = {
          val nc = de.unihamburg.informatik.nosqlmark.repl.REPL.interp.createNoSQLMarkContext()
          nc
        }
        import play.api.libs.json._
        import de.unihamburg.informatik.nosqlmark.api._
        import de.unihamburg.informatik.nosqlmark.repl.NoSQLMarkContext._"""

  override def createInterpreter: Unit = {
    super.createInterpreter
    intp.interpret(initialCommands)
  }

  override def prompt = "No5QLM4rk->"

  private val blockedCommands = Set("implicits", "javap", "power", "type", "kind")

  // Standard commands
  lazy val nosqlmarkStandardCommands: List[NoSQLMarkILoop.this.LoopCommand] =
    standardCommands.filter(cmd => !blockedCommands(cmd.name))

  // Available commands
  override def commands: List[LoopCommand] = nosqlmarkStandardCommands

  def createNoSQLMarkContext(): NoSQLMarkContext = {
    nosqlmarkContext = new NoSQLMarkContext()
    nosqlmarkContext
  }
}

object NoSQLMarkILoop  {

  def run(code: String, sets: Settings = new Settings): String = {
    import java.io.{BufferedReader, OutputStreamWriter, StringReader}
    stringFromStream { ostream =>
      Console.withOut(ostream) {
        val input = new BufferedReader(new StringReader(code))
        val output = new JPrintWriter(new OutputStreamWriter(ostream), true)
        val repl = new NoSQLMarkILoop(input, output)

        if (sets.classpath.isDefault)
          sets.classpath.value = sys.props("java.class.path")

        repl process sets
      }
    }
  }
  def run(lines: List[String]): String = run(lines.map(_ + "\n").mkString)
}
