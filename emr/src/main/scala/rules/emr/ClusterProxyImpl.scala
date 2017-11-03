package rules.emr

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce
import rules.{HasOwner, Wire}
import com.amazonaws.services.elasticmapreduce.model.AddJobFlowStepsRequest
import rules.emr.ClusterManager.RunningCluster
import rules.emr.ClusterStepStateWire.StepState

class ClusterProxyImpl(
    emr: AmazonElasticMapReduce,
    target: RunningCluster,
    demandWire: ActorRef[Wire.Command[List[Step]]],
    stepsWire: ActorRef[Wire.Command[Map[StepName, StepState]]]
)(implicit override val owner: rx.Ctx.Owner)
    extends ClusterDemandInvariant
    with HasOwner {
  import ClusterProxyImpl._

  private def scheduleStep(ctx: ActorContext[ClusterProxyImpl.Command])(
      steps: List[Step]): Unit = if (steps.nonEmpty) {

    scheduled() = scheduled.now ++ steps.map(_.stepName)

    val req = new AddJobFlowStepsRequest()
      .withSteps(steps.map(_.config): _*)
      .withJobFlowId(target.clusterId.value)

    try {
      emr.addJobFlowSteps(req)
    } catch {
      case e: Exception =>
        ctx.system.log
          .warning("Attempting to addJobFlowStep failed: {}!", e.getMessage)
        val toRemove = steps.map(_.stepName).toSet
        scheduled() = scheduled.now diff toRemove
    }
  }

  val init: Behavior[Command] = Actor.deferred { ctx =>
    ctx.spawnAnonymous(Wire.toVar(demand, demandWire))
    ctx.spawnAnonymous(Wire.toOptionVar(detected, stepsWire))

    //Actually Schedule on emr
    invariant.foreach { scheduleStep(ctx) }

    Actor.immutable { (_, msg) =>
      msg match {
        case Refresh => scheduled() = Set.empty
      }
      Actor.same
    }
  }

}

object ClusterProxyImpl {
  sealed trait Command
  case object Refresh extends Command
  import concurrent.duration._

  def apply(
      emr: AmazonElasticMapReduce,
      target: RunningCluster,
      pollStepsInterval: FiniteDuration,
      demandWire: ActorRef[Wire.Command[List[Step]]]
  )(implicit owner: rx.Ctx.Owner): Behavior[Command] = {
    Actor.deferred[Command] { ctx =>
      val stepsWire = ctx.spawn(
        ClusterStepStateWire(emr, target, pollStepsInterval),
        "cluster-steps"
      )
      val proxy = new ClusterProxyImpl(emr, target, demandWire, stepsWire)
      proxy.init
    }
  }
}
