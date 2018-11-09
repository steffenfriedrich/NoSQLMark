package de.unihamburg.informatik.nosqlmark

import java.io.File
import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory
import de.unihamburg.informatik.nosqlmark.actors.{BackbenchService}
import org.rogach.scallop.ScallopConf

/**
 * Created by Steffen Friedrich on 29.04.2015.
 */
object BackBench extends App {
  val params = new Parameters(args)
  println(params.configFile())
  val config = ConfigFactory.parseFile(new File(params.configFile())).withFallback(ConfigFactory.load())

  val system = ActorSystem("NoSQLMarkCluster", config.getConfig("backbench").withFallback(config))
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = BackbenchService.props(),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system).withRole(Some("backbench"))),
    name = "backbench")
  }


/**
 * Created by Steffen Friedrich on 11.05.2015.
 */
class Parameters(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("The NoSQLMark Backend called Backbench 0.1.0")
  banner( """"Usage: sbt "project backbench" "run [options]"
            |Options:
            | """.stripMargin)

  val configFile = opt[String](
    default = if (new File("./config/nosqlmark.conf").isFile)
      Some("./config/nosqlmark.conf")
    else Some("../config/nosqlmark.conf"),
    validate = (conf => {
      val file = new File(conf)
      file.isFile
    }), descr = "Config file for user defined akka configuration.")
}

