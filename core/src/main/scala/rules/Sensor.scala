package rules

import rx._

class Sensor[A](name: String)

object Sensor {
  def apply[A](inp: String): Var[A] = ???
}
