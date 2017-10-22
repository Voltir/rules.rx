package rules.aws.emr

import java.time.LocalDateTime

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce
import com.amazonaws.services.elasticmapreduce.model.AddJobFlowStepsRequest
import rules.Signal
import rules.aws.emr.ClusterManager.RunningCluster
import rx._

class ClusterProxy(emr: AmazonElasticMapReduce,
                   ctx: ActorContext[ClusterProxy.Command],
                   target: RunningCluster,
                   demandSensor: ActorRef[Signal.Command[List[Step]]])(
    implicit owner: rx.Ctx.Owner) {
  import ClusterProxy._

  sealed trait StepStateDefinition
  case object Scheduled extends StepStateDefinition
  case object Pending extends StepStateDefinition
  case object Active extends StepStateDefinition
  case object Completed extends StepStateDefinition
  case object Terminated extends StepStateDefinition

  case class StepState(step: Step,
                       state: StepStateDefinition,
                       changedAt: LocalDateTime)

  val demand: Var[List[Step]] = Var(List.empty)

  val activeSteps
    : Var[Option[Map[StepName, StepState]]] = Var(Some(Map.empty)) // skip over initial state query for test

  private val processing: Rx[Option[Set[StepName]]] = Rx {
    activeSteps().map(_.keySet)
  }

  private val toStart = Rx {
    processing() match {
      case Some(active) =>
        demand().filter(s => !active.contains(s.stepName))

      // Don't start any steps unless the current state of the running steps is known
      case None => List.empty
    }
  }

  toStart.filter(_.isEmpty).foreach { steps =>
    val req = new AddJobFlowStepsRequest()
      .withSteps(steps.map(_.config): _*)
      .withJobFlowId(target.clusterId.value)

    val now = LocalDateTime.now
    activeSteps() = activeSteps.now.map { active =>
      active ++ Map(
        steps.map(s => s.stepName -> StepState(s, Scheduled, now)): _*)
    }
  // emr.addJobFlowSteps(req)
  }

  activeSteps.foreach { xxx =>
    println(s"--------- Cluster ${target.name} ---------")
    println(xxx.getOrElse(Map.empty).mkString("\n"))
  }

  // Get Current State

  val init: Behavior[Command] = Actor.deferred { ctx =>
    Actor.withTimers { timers =>
      Actor.ignore
    }
  }

  ctx.spawnAnonymous(Signal.collectToVar(demand, demandSensor))
}

object ClusterProxy {

  sealed trait Command
  case object ListSteps extends Command

  def apply(emr: AmazonElasticMapReduce,
            target: RunningCluster,
            demand: ActorRef[Signal.Command[List[Step]]])(
      implicit owner: rx.Ctx.Owner): Behavior[Command] =
    Actor.deferred { ctx =>
      val wurt = new ClusterProxy(emr, ctx, target, demand)
      wurt.init
    }
}
