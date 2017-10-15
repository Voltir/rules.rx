package rules

import akka.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.typed.scaladsl.{Actor, ActorContext}

class Subscribers[Reply] {
  private class Impl(ctx: ActorContext[Subscribers.Command[Reply]])
      extends Actor.MutableBehavior[Subscribers.Command[Reply]] {
    var current: Reply = _
    val dudes = collection.mutable.Buffer.empty[ActorRef[Reply]]

    override def onMessage(msg: Subscribers.Command[Reply])
      : Behavior[Subscribers.Command[Reply]] = {
      msg match {
        case Subscribers.Subscribe(sub) =>
          println(s"${ctx.self.path}: New sub -> $sub")
          dudes.append(sub)
          ctx.watch(sub)
          if (current != null) {
            sub ! current
          }

        case Subscribers.Notify(value) =>
          println(s"${ctx.self.path}: Notify(${dudes.length}): $value")
          dudes.foreach(_ ! value)
      }
      this
    }

    override def onSignal: PartialFunction[Signal, Behavior[Subscribers.Command[Reply]]] = {
      case Terminated(ref) =>
        ctx.system.log.info("Subscriber {} is TERMINATED", ref)
        dudes -= ref.asInstanceOf[ActorRef[Reply]]
        this
    }
  }

  val behavior: Behavior[Subscribers.Command[Reply]] =
    Actor.mutable[Subscribers.Command[Reply]](ctx => new Impl(ctx))
}

object Subscribers {
  sealed trait Command[T]
  case class Subscribe[T](replyTo: ActorRef[T]) extends Command[T]
  case class Notify[T](value: T) extends Command[T]

  def forward[T](ref: ActorRef[Subscribers.Command[T]]): Behavior[T] =
    Actor.immutable { (_, msg) =>
      ref ! Subscribers.Notify(msg)
      Actor.same
    }
}
