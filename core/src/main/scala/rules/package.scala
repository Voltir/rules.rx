import rx.{Rx, Var}

package object rules {
  sealed trait Sensed[+T]
  case class Ready[T](value: T) extends Sensed[T]
  case object NotReady extends Sensed[Nothing]

  type Sense[X] = Var[Sensed[X]]

  object Sensezz {
    def init[A]: Var[Sensed[A]] = Var(NotReady)
  }
  def AllReady[A, B, X](a: Sense[A], b: Sense[B])(f: A => B => X)(implicit owner: rx.Ctx.Owner) = Rx {
    (a(), b()) match {
      case (Ready(aa), Ready(bb)) => f(aa)(bb)
    }
  }
}
