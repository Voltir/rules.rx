package rules

import collection.mutable.Stack
import org.scalatest._
import rules.internal.TestIt

//@TestIt("Int" -> 0, "String" -> "SuperCool")
//trait Wurt {
//
//}

class FooSpec extends FlatSpec with Matchers {

  "A Stack" should "pop values in last-in-first-out order" in {
    val stack = new Stack[Int]
    stack.push(1)
    stack.push(2)
    stack.pop() should be (2)
    stack.pop() should be (1)
  }

  it should "throw NoSuchElementException if an empty stack is popped" in {
    val emptyStack = new Stack[Int]
    a [NoSuchElementException] should be thrownBy {
      emptyStack.pop()
    }
  }

//  it should "lolwtf" in {
//    println(Wurt.aaa)
//    println(Wurt().omg)
//    val neat = Wurt()
//    println(neat.anInt)
//    println(neat.anString)
//  }

//  it should "zzz" in {
//    rules.construction.generate[ExampleRules](42)
//  }

}