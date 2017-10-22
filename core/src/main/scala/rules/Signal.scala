package rules

import akka.typed.{ActorRef, Behavior, Terminated}
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import rx.Var

import scala.concurrent.duration.FiniteDuration

abstract class Signal[A](
    ctx: ActorContext[Signal.Command[A]]
) extends Actor.MutableBehavior[Signal.Command[A]] {
  import Signal._
  private val subscribers = collection.mutable.Set.empty[ActorRef[Raise[A]]]

  protected def raise: Option[A]

  override def onMessage(msg: Command[A]): Behavior[Command[A]] = {
    msg match {
      case Tick =>
        raise.foreach { value =>
          subscribers.foreach(_ ! Raise(value))
        }

      case Register(replyTo) =>
        subscribers += replyTo.narrow[Raise[A]]
        ctx.watch(replyTo)

    }
    Actor.same
  }

  override def onSignal
    : PartialFunction[akka.typed.Signal, Behavior[Command[A]]] = {
    case Terminated(ref) =>
      ctx.system.log.info("Subscriber {} is TERMINATED", ref)
      subscribers -= ref.asInstanceOf[ActorRef[Signal.Raise[A]]]
      this
  }
}

object Signal {
  case class Raise[A](value: A)

  sealed trait Command[+A]
  case class Register[A](replyTo: ActorRef[Raise[A]]) extends Command[A]
  case object Tick extends Command[Nothing]
  case object TickKey

  def collectToVar[A](sink: Var[A], ref: ActorRef[Command[A]]): Behavior[Unit] =
    Actor.deferred { ctx =>
      val internal = Actor.immutable[Signal.Raise[A]] { (_, raised) =>
        sink() = raised.value
        Actor.same
      }
      ref ! Register(ctx.spawnAnonymous(internal))
      Actor.empty
    }

  def behavior[A](period: FiniteDuration)(
      factory: ActorContext[Command[A]] => Actor.MutableBehavior[Command[A]]) = {
    Actor.withTimers[Command[A]] { timers =>
      timers.startPeriodicTimer(Signal.TickKey, Signal.Tick, period)
      Actor.mutable[Command[A]] { ctx =>
        factory(ctx)
      }
    }
  }
}
