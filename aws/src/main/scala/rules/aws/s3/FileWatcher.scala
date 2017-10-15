package rules.aws.s3

import akka.typed.{ActorSystem, Behavior, Terminated}
import akka.typed.scaladsl.{Actor, TimerScheduler}



object Foo {
  import scala.concurrent.duration.FiniteDuration

  def withLifetime[T](lifetime: FiniteDuration, death: T, timerKey: Any)(
      factory: TimerScheduler[T] => Behavior[T]): Behavior[T] = {
    Actor.withTimers[T] { timers =>
      timers.startSingleTimer(timerKey, death, lifetime)
      Actor.deferred { ctx =>
        val worker = ctx.spawn(factory(timers), "run")
        ctx.watch(worker)
        Actor.immutable[T] { (_, msg) =>
          msg match {
            case `death` =>
              println("---> KILLED <---")
              Actor.stopped
            case other =>
              worker ! other
              Actor.same
          }
        } onSignal {
          case (_, Terminated(ref)) if ref == worker =>
            ctx.system.log.debug("Runner terminated", ref)
            Actor.stopped
        }
      }
    }
  }

  def withPolling[T](frequency: FiniteDuration, tick: T, tickKey: Any)(
      factory: TimerScheduler[T] => Behavior[T])
    : TimerScheduler[T] => Behavior[T] = { timers =>
    timers.startPeriodicTimer(tickKey, tick, frequency)
    factory(timers)
  }

}

object FileWatcher {
  import concurrent.duration._

  // Behavior Messages
  sealed trait Command
  sealed trait Internal extends Command

  case object Tick extends Internal
  case object Death extends Internal

  // Timer Key
  case object Polling
  case object DeathKey

  val behavior: Behavior[Command] =
    Foo.withLifetime[Command](5.seconds, Death, DeathKey) {
      Foo.withPolling[Command](1.seconds, Tick, Polling) { timers =>
        Actor.immutable { (_, msg) =>
          println("NICE! " + msg)
          Actor.same
        }
      }
    }
}

object Test extends App {
  import scala.io.StdIn

  val root = Actor.deferred[Nothing] { ctx =>
    val test = ctx.spawn(FileWatcher.behavior, "neat")
    Actor.empty
  }

  val system = ActorSystem[Nothing](root, "HelloWorld")
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