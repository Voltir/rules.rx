package rules.emr

import java.time.{LocalDateTime, ZoneId}

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce
import com.amazonaws.services.elasticmapreduce.model.{
  AddJobFlowStepsRequest,
  ListStepsRequest,
  StepState => EmrStepState
}
import rules.Wire
import rules.emr.ClusterManager.RunningCluster
import rx._

import collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class ClusterProxy(emr: AmazonElasticMapReduce,
                   resyncInterval: FiniteDuration,
                   ctx: ActorContext[ClusterProxy.Command],
                   target: RunningCluster,
                   demandSensor: ActorRef[Wire.Command[List[Step]]])(
    implicit owner: rx.Ctx.Owner) {
  import ClusterProxy._

  sealed trait StepStateDefinition
  case object Scheduled extends StepStateDefinition
  case class Detected(state: EmrStepState) extends StepStateDefinition

  case class StepState(stepName: StepName,
                       state: StepStateDefinition,
                       changedAt: LocalDateTime)

  val demand: Var[List[Step]] = Var(List.empty)

  val activeSteps: Var[Option[Map[StepName, StepState]]] = Var(None)

  private val processing: Rx[Option[Set[StepName]]] = Rx {
    activeSteps().map(_.keySet)
  }

  private val toStart: Rx[List[Step]] = Rx {
    val currentDemand = demand()
    processing() match {
      case Some(active) =>
        currentDemand.filter(s => !active.contains(s.stepName))

      // Don't start any steps unless the current state of the running steps is known
      case None => List.empty
    }
  }

  private val obs = toStart.filter(_.nonEmpty).foreach { steps =>
    val req = new AddJobFlowStepsRequest()
      .withSteps(steps.map(_.config): _*)
      .withJobFlowId(target.clusterId.value)

    val now = LocalDateTime.now
    activeSteps() = activeSteps.now.map { active =>
      active ++ Map(
        steps.map(s => s.stepName -> StepState(s.stepName, Scheduled, now)): _*)
    }
    try {
      emr.addJobFlowSteps(req)
    } catch {
      case e: Exception =>
        ctx.system.log
          .warning("Attempting to addJobFlowStep failed: {}!", e.getMessage)
        val toRemove = steps.map(_.stepName).toSet
        activeSteps() =
          activeSteps.now.map(_.filterKeys(k => !toRemove.contains(k)))
    }
  }

  private val recencyFilter: ((StepName, StepState)) => Boolean = {
    case (_, state) =>
      val recencyCutoff = LocalDateTime.now.minusDays(2)
      state.changedAt.isAfter(recencyCutoff)
  }

  private def sync(): Unit =
    try {
      val req = new ListStepsRequest()
        .withClusterId(target.clusterId.value)
        .withStepStates(EmrStepState.PENDING, EmrStepState.RUNNING)

      val listed = emr.listSteps(req)

      val detected = listed.getSteps.asScala.toList.map { summary =>
        val name = StepName(summary.getName)
        val createdAt = summary.getStatus.getTimeline.getCreationDateTime
        val localCreatedAt =
          createdAt.toInstant
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime
        val state =
          StepState(
            name,
            Detected(EmrStepState.fromValue(summary.getStatus.getState)),
            localCreatedAt)
        name -> state
      }

      val synced = Map(detected: _*)

      val next = activeSteps.now
        .getOrElse(Map.empty)
        .filterKeys(keep => !synced.keySet.contains(keep))
        .filter(recencyFilter)
      activeSteps() = Some(next ++ synced)
    } catch {
      case e: Exception =>
        ctx.system.log.warning("EMR failed to sync: {}", e.getMessage)
    }

  val init: Behavior[Command] = Actor.deferred { ctx =>
    Actor.withTimers { timers =>
      timers.startPeriodicTimer(ResyncKey, Resync, resyncInterval)

      Actor.immutable { (_, msg) =>
        msg match {
          case Resync    => sync()
          case ListSteps => //todo
        }
        Actor.same
      }
    }
  }

  ctx.spawnAnonymous(Wire.toVar(demand, demandSensor))
}

object ClusterProxy {

  sealed trait Command
  case object ListSteps extends Command

  sealed trait Internal extends Command
  case object Resync extends Internal
  case object ResyncKey

  def apply(emr: AmazonElasticMapReduce,
            resyncInterval: FiniteDuration,
            target: RunningCluster,
            demand: ActorRef[Wire.Command[List[Step]]])(
      implicit owner: rx.Ctx.Owner): Behavior[Command] =
    Actor.deferred { ctx =>
      val wurt = new ClusterProxy(emr, resyncInterval, ctx, target, demand)
      wurt.init
    }
}
