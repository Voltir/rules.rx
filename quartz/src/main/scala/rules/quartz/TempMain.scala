package rules.quartz

import java.time.ZonedDateTime

import akka.typed.Behavior
import akka.typed.scaladsl.Actor
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.typed.scaladsl.adapter._
import com.typesafe.config.ConfigFactory

object TestQuartz {
  val behavior: Behavior[ZonedDateTime] = Actor.immutable { (ctx, msg) =>
    println("TICKKKKKKKKKK")
    println("SuperCool: " + msg)
    Actor.same
  }
}

object TempMain extends App {
  import scala.io.StdIn

  println("Quartz Test")

  val testConfig =
    """
      |akka {
      |  quartz {
      |    schedules {
      |      Every30Seconds {
      |        description = "A cron job that fires off every 30 seconds"
      |        expression = "*/5 * * ? * *"
      |      }
      |    }
      |  }
      |}
    """.stripMargin
  val config = ConfigFactory.parseString(testConfig)

  //Untyped actor system
  val system = akka.actor.ActorSystem("quartz-test", config = Some(config))

  val noti = system.spawn(TestQuartz.behavior, "bleh")
  val refd = system.spawn(CronSensor.behavior("Every30Seconds", noti),
                          "every-30-seconds")

  QuartzSchedulerExtension(system).schedule("Every30Seconds",
                                            refd.toUntyped,
                                            CronSensor.Trigger)

  try {
    println("Press ENTER to exit the system")
    StdIn.readLine()
  } catch {
    case e: Exception =>
      println("---------------------> NOOOOOOOOOOOo <----------------")
      println(e.getMessage)
  } finally {
    println("DEATH!")
    system.terminate()
  }

}
