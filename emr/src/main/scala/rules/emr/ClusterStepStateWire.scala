package rules.emr

import akka.typed.Behavior
import akka.typed.scaladsl.ActorContext
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce
import com.amazonaws.services.elasticmapreduce.model.{
  ListStepsRequest,
  StepState => EmrStepState
}
import rules.Wire
import rules.emr.ClusterManager.RunningCluster

import collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import java.time.{LocalDateTime, ZoneId}

class ClusterStepStateWire(
    emr: AmazonElasticMapReduce,
    target: RunningCluster,
    ctx: ActorContext[Wire.Command[Map[StepName, ClusterStepStateWire.StepState]]]
) extends Wire[Map[StepName, ClusterStepStateWire.StepState]](ctx) {
  import ClusterStepStateWire._

  override protected def raise: Option[Map[StepName, StepState]] = {
    try {
      val req = new ListStepsRequest()
        .withClusterId(target.clusterId.value)
        .withStepStates(
          EmrStepState.PENDING,
          EmrStepState.RUNNING
        )

      val listed = emr.listSteps(req)

      val update = listed.getSteps.asScala.toList.map { summary =>
        val name = StepName(summary.getName)

        val time = summary.getStatus.getTimeline.getCreationDateTime.toInstant
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime

        val state =
          StepState(EmrStepState.fromValue(summary.getStatus.getState), time)
        name -> state
      }
      Some(Map(update: _*))
    } catch {
      case e: Exception =>
        ctx.system.log.warning("EMR failed to sync: {}", e.getMessage)
        None
    }
  }
}

object ClusterStepStateWire {

  final case class StepState(
      state: EmrStepState,
      changedAt: LocalDateTime
  )

  def apply(
      emr: AmazonElasticMapReduce,
      target: RunningCluster,
      resyncInterval: FiniteDuration
  ): Behavior[Wire.Command[Map[StepName, StepState]]] = {
    Wire.behavior(resyncInterval) { ctx =>
      new ClusterStepStateWire(emr, target, ctx)
    }
  }
}
