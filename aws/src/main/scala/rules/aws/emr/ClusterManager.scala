package rules.aws.emr

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import cats.instances.list._
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import com.amazonaws.services.elasticmapreduce.model.{ Unit => _, _}
import rules.{AggregateSignal, HasOwner, Signal, behaviors}
import rules.aws.emr.ClusterManager.RunningCluster
import rx._

import scala.concurrent.duration._
import scala.collection.JavaConverters._

class ClusterManager(emr: AmazonElasticMapReduceClient,
                     config: ClusterManager.Config,
                     stepSensor: ActorRef[Signal.Command[List[Step]]],
                     ctx: ActorContext[Signal.Command[List[RunningCluster]]])(
    implicit override val owner: rx.Ctx.Owner)
    extends AggregateSignal[Int, List[RunningCluster]](ctx)
    with HasOwner {

  import ClusterManager._

  private val steps: Var[List[Step]] = Var(List.empty)

  private val running: Var[Option[List[RunningCluster]]] = Var(None)

  override protected val desired: Rx[Set[Int]] = Rx {
    val numSteps = steps().length
    val maxClusters =
      Math.max(numSteps / config.stepsPerCluster, config.maxCluster)
    (1 to maxClusters).toSet
  }

  override protected def start(
      aggregate: ActorRef[AggregateSignal.Command[Int, List[RunningCluster]]],
      key: Int
  ): Behavior[Unit] =  {
    Actor.ignore[Unit]
  }

  private def isManaged(clusterName: String): Boolean = ???

  // Internal Worker to track state of EMR clusters
  case object PollCluster
  private val pollClusters: Behavior[PollCluster.type] =
    behaviors.withPolling2(config.pollClusterInterval, PollCluster) { _ =>
      Actor.immutable[PollCluster.type] { (_, _) =>
        try {
          val req = new ListClustersRequest().withClusterStates(
            ClusterState.RUNNING,
            ClusterState.STARTING,
            ClusterState.BOOTSTRAPPING,
            ClusterState.WAITING)

          val results = emr.listClusters(req)
          val meh = results.getClusters.asScala.toList
            .filter(r => isManaged(r.getName))
            .map { r =>
              RunningCluster(ClusterId(r.getId), ClusterName(r.getName))
            }
          running() = Some(meh)
        } catch {
          case e: Exception =>
            ctx.system.log.error("Failed to list emr clusters! {}", e)
            running() = None
        }
        Actor.same
      }
    }
  ctx.spawnAnonymous(pollClusters)

  // Bind signal to local Var
  ctx.spawnAnonymous(Signal.collectToVar(steps, stepSensor))
}

object ClusterManager {
  case class Config(maxCluster: Int = 3,
                    stepsPerCluster: Int = 5,
                    pollClusterInterval: FiniteDuration = 30.seconds)
  case class ClusterId(value: String) extends AnyVal
  case class ClusterName(value: String) extends AnyVal

  case class RunningCluster(clusterId: ClusterId, name: ClusterName)
}
