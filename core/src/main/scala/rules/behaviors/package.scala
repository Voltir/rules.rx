package rules

import akka.typed.{Behavior, Terminated}
import akka.typed.scaladsl.{Actor, TimerScheduler}

import scala.concurrent.duration._

package object behaviors {

  sealed trait DeathWatch
  case object Die extends DeathWatch
  object DeathKey

  def withLifetime[T](lifetime: FiniteDuration)(
      wrapped: Behavior[T]): Behavior[T] = {
    Actor
      .deferred[Any] { ctx =>
        Actor.withTimers[Any] { timers =>
          timers.startSingleTimer(DeathKey, Die, lifetime)
          val worker = ctx.spawnAnonymous(wrapped)
          Actor.immutable[Any] { (_, msg) =>
            msg match {
              case Die =>
                ctx.system.log.debug("Expired {}", ctx.self)
                Actor.stopped
              case x =>
                worker ! x.asInstanceOf[T]
                Actor.same
            }
          } onSignal {
            case (_, Terminated(ref)) if ref == worker =>
              ctx.system.log.debug("Worker terminated", ref)
              Actor.stopped
          }
        }
      }
      .narrow[T]
  }

  def withPolling[T](frequency: FiniteDuration, tick: T, tickKey: Any)(
      factory: TimerScheduler[T] => Behavior[T])
    : TimerScheduler[T] => Behavior[T] = { timers =>
    timers.startPeriodicTimer(tickKey, tick, frequency)
    factory(timers)
  }

  case object PollingKey
  def withPolling2[T](frequency: FiniteDuration, tick: T)(
      factory: TimerScheduler[T] => Behavior[T]) = Actor.withTimers[T] {
    timers =>
      timers.startPeriodicTimer(PollingKey, tick, frequency)
      factory(timers)
  }

  // The inner handler for Tick needs to reschedule the timer manually
  def withRepeat[T](tick: T,
                    tickKey: Any,
                    firstTickWait: FiniteDuration = 1.second)(
      next: TimerScheduler[T] => Behavior[T]): Behavior[T] = {
    Actor.withTimers { timers =>
      timers.startSingleTimer(tickKey, tick, firstTickWait)
      next(timers)
    }
  }

  def finiteRepeat[T](lifetime: FiniteDuration)(tick: T, tickKey: Any)(
      next: TimerScheduler[T] => Behavior[T]) = withLifetime[T](lifetime) {
    withRepeat[T](tick, tickKey) { next }
  }
}
