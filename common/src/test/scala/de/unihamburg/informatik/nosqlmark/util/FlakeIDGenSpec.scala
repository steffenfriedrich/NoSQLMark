package de.unihamburg.informatik.nosqlmark.util

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Created by Friedrich on 07.07.2015.
 */
class FlakeIDGenSpec extends WordSpecLike with MustMatchers {
  implicit lazy val system = ActorSystem()

  import ExecutionContext.Implicits.global

  "SnowflakeIDs" must {
    "be unique" in {
      val customConf = ConfigFactory.parseString( """
      akka.loglevel = INFO
                                                  """)
      val system = ActorSystem("SnowFlakeSystem", ConfigFactory.load(customConf))
      val consumer = (1 to 10).map(i =>
        system.actorOf(SnowFlakeConsumer.props(i), name = "consumer-" + i))

      implicit val timeout = Timeout(20 seconds)
      val futures = for (c <- consumer) yield (ask(c, "startSnowflake").mapTo[Set[String]])
      val future = Future.sequence(futures)

      val result = Await.result(future, 10 seconds)

      var unique = true
      for (i <- 1 to 9)
        for (j <- 1 to 9)
          if (i < j && i != j && !((result(i) & result(j)).isEmpty)) unique = false

      assert(unique)
    }
  }

  "BadIDs" must {
    "be not unique" in {
      val customConf = ConfigFactory.parseString( """
      akka.loglevel = INFO
                                                  """)
      val system = ActorSystem("SnowFlakeSystem", ConfigFactory.load(customConf))
      val consumer = (1 to 10).map(i =>
        system.actorOf(SnowFlakeConsumer.props(i), name = "consumer-" + i))

      implicit val timeout = Timeout(20 seconds)
      val futures = for (c <- consumer) yield (ask(c, "startBadID").mapTo[Set[String]])
      val future = Future.sequence(futures)

      val result = Await.result(future, 10 seconds)

      var unique = true
      for (i <- 1 to 9)
        for (j <- 1 to 9)
          if (i < j && i != j && !((result(i) & result(j)).isEmpty)) unique = false

      assert(!unique)
    }
  }

  object SnowFlakeConsumer {
    def props(i: Int): Props = Props(new SnowFlakeConsumer(i))
  }

  class SnowFlakeConsumer(i: Int) extends Actor with ActorLogging {
    var orig_sender: ActorRef = null
    var cnr: Int = 0
    var ids = scala.collection.mutable.Set[String]()

    def receive = {
      case "startSnowflake" =>
        orig_sender = sender
        self ! "snowflake"
      case "snowflake" =>
        cnr match {
          case a if a < 100 =>
            val id = FlakeIDGen.getSnowflakeMacIdString(NetworkUtil.mac)
            ids += id
            cnr += 1
            self ! "snowflake"
          case _ => orig_sender ! Set() ++ ids
        }
      case "startBadID" =>
        orig_sender = sender
        self ! "badID"
      case "badID" =>
        cnr match {
          case a if a < 100 =>
            val id = System.currentTimeMillis().toString
            ids += id
            cnr += 1
            self ! "badID"
          case _ => orig_sender ! Set() ++ ids
        }
    }
  }

  "IPCounterIDS" must {
    " be gernerated without bufferoverflow" in {
      (1 to 1000).foreach( i => FlakeIDGen.getIpCounterIDString(NetworkUtil.ip)
      )
    }
  }
}
