package rules

import akka.typed.{ActorRef, Behavior, Signal => ActorSignal, Terminated}
import akka.typed.scaladsl.{Actor, ActorContext}

class Subscribersz[Reply] {
  private class Impl(ctx: ActorContext[Subscribersz.Command[Reply]])
      extends Actor.MutableBehavior[Subscribersz.Command[Reply]] {
    var current: Reply = _
    val dudes = collection.mutable.Buffer.empty[ActorRef[Reply]]

    override def onMessage(msg: Subscribersz.Command[Reply])
      : Behavior[Subscribersz.Command[Reply]] = {
      msg match {
        case Subscribersz.Subscribe(sub) =>
          ctx.system.log.info(s"New sub: ${sub.path.name}")
          dudes.append(sub)
          ctx.watch(sub)
          if (current != null) {
            sub ! current
          }

        case Subscribersz.Notify(value) =>
          dudes.foreach(_ ! value)
      }
      this
    }

    override def onSignal
      : PartialFunction[ActorSignal, Behavior[Subscribersz.Command[Reply]]] = {
      case Terminated(ref) =>
        ctx.system.log.info("Subscriber {} is TERMINATED", ref)
        dudes -= ref.asInstanceOf[ActorRef[Reply]]
        this
    }
  }

  val behavior: Behavior[Subscribersz.Command[Reply]] =
    Actor.mutable[Subscribersz.Command[Reply]](ctx => new Impl(ctx))
}

object Subscribersz {
  sealed trait Command[T]
  case class Subscribe[T](replyTo: ActorRef[T]) extends Command[T]
  case class Notify[T](value: T) extends Command[T]

  def forward[T](ref: ActorRef[Subscribersz.Command[T]]): Behavior[T] =
    Actor.immutable { (_, msg) =>
      ref ! Subscribersz.Notify(msg)
      Actor.same
    }
}
