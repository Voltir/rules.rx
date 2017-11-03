package rules

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.Actor
import cats.Monoid
import rx._

import scala.concurrent.duration.FiniteDuration

object AggregateSignal {
  sealed trait Command[Key, Value]
  case class Update[Key, Value](key: Key, value: Value)
      extends Command[Key, Value]
}

abstract class AggregateWire[Key, Value: Monoid] { self: HasOwner =>

  import AggregateWire._

  protected def desired: Rx[Set[Key]]

  protected def period: FiniteDuration

  protected def start(
      aggregate: ActorRef[Command[Key, Value]],
      key: Key
  ): Behavior[Unit]

  private case class State(ref: ActorRef[Unit], current: Option[Value])

  private val state: Var[Map[Key, State]] = Var(Map.empty)

  private val mempty = Monoid[Value].empty

  val canSignal: Rx[Boolean] = Rx {
    state().valuesIterator.forall(_.current.isDefined)
  }

  val current: Rx[Value] = Rx {
    Monoid[Value].combineAll(
      state().mapValues(_.current.getOrElse(mempty)).values)
  }

  private lazy val agg = Actor.immutable[Command[Key, Value]] { (ctx, msg) =>
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

  lazy val behavior: Behavior[Wire.Command[Value]] =
    Actor.deferred { ctx =>
      val aggRef = ctx.spawn(agg, "aggregate")
      desired.foreach { wanted =>
        val active = state.now.keySet
        wanted.diff(active).foreach { w =>
          val child = ctx.spawnAnonymous(start(aggRef, w))
          state() = state.now + (w -> State(child, None))
        }
        active.diff(wanted).foreach { notWanted =>
          state.now.get(notWanted).map(_.ref).foreach(ctx.stop)
          state() = state.now - notWanted
        }
        ctx.system.log.warning(
          "Aggregate ({}) now has {} children (Started: {}, Stopped: {})",
          ctx.self.path.name,
          ctx.children.size,
          wanted.diff(active).size,
          active.diff(wanted).size
        )
      }
      Wire.behavior[Value](period)(ctx =>
        new Wire[Value](ctx) {
          override protected def raise: Option[Value] = {
            if (canSignal.now) Some(current.now)
            else None
          }
      })
    }
}

object AggregateWire {
  sealed trait Command[Key, Value]
  case class Update[Key, Value](key: Key, value: Value)
      extends Command[Key, Value]
}
