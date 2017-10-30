package rules

import akka.typed.{ActorRef, Behavior, Terminated}
import akka.typed.scaladsl.{Actor, ActorContext}
import rx.Var

import scala.concurrent.duration.FiniteDuration

abstract class Wire[A](ctx: ActorContext[Wire.Command[A]])
    extends Actor.MutableBehavior[Wire.Command[A]] {
  import Wire._
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
      subscribers -= ref.asInstanceOf[ActorRef[Wire.Raise[A]]]
      this
  }

}

object Wire {
  case class Raise[A](value: A)

  sealed trait Command[+A]
  case class Register[A](replyTo: ActorRef[Raise[A]]) extends Command[A]
  private case object Tick extends Command[Nothing]
  private case object TickKey

  def toVar[A](sink: Var[A], ref: ActorRef[Command[A]]): Behavior[Unit] =
    Actor.deferred { ctx =>
      val internal = Actor.immutable[Wire.Raise[A]] { (_, raised) =>
        sink() = raised.value
        Actor.same
      }
      ref ! Register(ctx.spawnAnonymous(internal))
      Actor.empty
    }

  def testWith[A](source: rx.Var[A],
                  period: FiniteDuration): Behavior[Command[A]] = {
    val factory = (ctx: ActorContext[Command[A]]) =>
      new Wire[A](ctx) {
        override protected def raise: Option[A] = Some(source.now)

    }
    behavior(period)(factory)
  }

  def fromRx[A](source: rx.Rx[A], period: FiniteDuration)(
      implicit owner: rx.Ctx.Owner): Behavior[Command[A]] = {
    val factory = (ctx: ActorContext[Command[A]]) =>
      new Wire[A](ctx) {
        //only signal if Rx has produced at least one value
        private var ready: Boolean = false
        source.triggerLater {
          ready = true
        }

        override protected def raise: Option[A] = {
          if (ready) Some(source.now)
          else None
        }
    }
    behavior(period)(factory)
  }

  def behavior[A](period: FiniteDuration)(
      factory: ActorContext[Command[A]] => Wire[A]): Behavior[Command[A]] = {
    Actor.withTimers[Command[A]] { timers =>
      timers.startPeriodicTimer(Wire.TickKey, Wire.Tick, period)
      Actor.mutable[Command[A]] { ctx =>
        factory(ctx)
      }
    }
  }
}
