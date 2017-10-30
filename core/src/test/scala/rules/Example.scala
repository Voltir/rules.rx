package rules

import rules.internal.Invariant
import rx._

//@Invariant
trait Examplez {
  val src1: Var[List[String]]
  val src2: Var[Int]

//  val invariant = Rx {
//    src1().map(_.toInt).filter(_ < src2())
//  }
}

//
//trait Examplez extends Rule[List[Int]] {
//  val src1: Var[List[String]] = Sensor[List[String]]("foo")
//  val src2: Var[Int] = Sensor[Int]("bar")
//
//  override val invariant = Rx {
//    src1().map(_.toInt).filter(_ < src2())
//  }
//}
//
//
//
//trait ExampleRules {
//  def rule1: Register[Examplez] = new Register[Examplez] {
//    (inp: List[Int]) => println("ok")
//  }
//
//  def rule2: Register[Examplez] = new Register[Examplez] {
//    (inp: List[Int]) => println("ok")
//  }
//}
//
