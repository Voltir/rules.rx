package rules

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import cats.Monoid
import rx._

abstract class AggregateSignal[Key, Value: Monoid](
    ctx: ActorContext[Signal.Command[Value]])
    extends Signal[Value](ctx) { self: HasOwner =>

  import AggregateSignal._

  protected def desired: Rx[Set[Key]]

  protected def start(aggregate: ActorRef[Command[Key, Value]],
                      key: Key): Behavior[Unit]

  private case class State(ref: ActorRef[Unit], current: Option[Value])

  private val state: Var[Map[Key, State]] = Var(Map.empty)

  private val mempty = Monoid[Value].empty

  override protected def raise: Option[Value] = {
    // Only produce a value if all active children have produced a state
    if (state.now.valuesIterator.forall(_.current.isDefined)) {
      val result = Monoid[Value].combineAll(
        state.now.mapValues(_.current.getOrElse(mempty)).values)
      Some(result)
    } else None
  }

  private val aggregatorBehavior = Actor.immutable[Command[Key, Value]] {
    (ctx, msg) =>
      msg match {
        case Update(key, value) =>
          if (state.now.contains(key)) {
            state() = state.now + (key -> state
              .now(key)
              .copy(current = Some(value)))
          }
      }
      Actor.same
  }

  private val agg = ctx.spawn(aggregatorBehavior, "aggregator")

  desired.foreach { wanted =>
    val active = state.now.keySet
    wanted.diff(active).foreach { w =>
      val child = ctx.spawnAnonymous(start(agg, w))
      state() = state.now + (w -> State(child, None))
    }
    active.diff(wanted).foreach { notWanted =>
      state.now.get(notWanted).map(_.ref).foreach(ctx.stop)
      state() = state.now - notWanted
    }
  }
}

object AggregateSignal {
  sealed trait Command[Key, Value]
  case class Update[Key, Value](key: Key, value: Value)
      extends Command[Key, Value]
}
