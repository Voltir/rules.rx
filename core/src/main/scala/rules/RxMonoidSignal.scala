package rules

import akka.typed.Behavior
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import cats.Monoid
import rx.Rx

import scala.concurrent.duration.FiniteDuration

class RxMonoidSignal[A: Monoid](
    src: Rx[A],
    ctx: ActorContext[Signal.Command[A]]
) extends Signal[A](ctx) {
  val mempty = Monoid[A].empty

  override protected def raise: Option[A] = {
    if (src.now != mempty) Some(src.now)
    else None
  }
}

object RxMonoidSignal {
  def apply[A: Monoid](period: FiniteDuration)(
      src: Rx[A]
  ): Behavior[Signal.Command[A]] = {
    Signal.behavior(period)(ctx => new RxMonoidSignal[A](src, ctx))
  }
}
