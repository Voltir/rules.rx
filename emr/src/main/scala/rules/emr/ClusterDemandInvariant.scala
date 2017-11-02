package rules.emr

import rules.HasOwner
import rx._

trait ClusterDemandInvariant { self: HasOwner =>

  val demand: Var[List[Step]] = Var(List.empty)

  val detected: Var[Option[Map[StepName, ClusterStateWire.StepState]]] =
    Var(None)

  val scheduled: Var[Set[StepName]] = Var(Set.empty)

  val invariant: Rx[List[Step]] = Rx {
    detected() match {
      case Some(active) =>
        for {
          d <- demand()
          if !active.keySet.contains(d.stepName) &&
            !scheduled().contains(d.stepName)
        } yield d
      case None =>
        List.empty
    }
  }
}