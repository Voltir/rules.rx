package rules.quartz

import java.time.ZonedDateTime

import akka.typed.scaladsl.Actor
import akka.typed.{ActorRef, Behavior}
import com.typesafe.config.ConfigFactory

object CronSensor {
  case object Trigger

  private val defaultTimezone = ConfigFactory.parseString("defaultTimezone = UTC")

  def behavior(cronName: String,
               notify: ActorRef[ZonedDateTime]): Behavior[Trigger.type] = {
    Actor.deferred { ctx =>
      // akka-quartz-scheduler uses akka configuration for all cron configs
      // it defaults to UTC
      val cronConfig =
        ctx.system.settings.config.getConfig(s"akka.quartz.schedules.$cronName")
      val zoneIdString = cronConfig.withFallback(defaultTimezone).getString("defaultTimezone")
      val zoneId = java.time.ZoneId.of(zoneIdString)
      Actor.immutable { (_, _) =>
        notify ! ZonedDateTime.now(zoneId)
        Actor.same
      }
    }
  }
}
