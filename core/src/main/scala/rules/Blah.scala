package rules

import akka.typed.{ActorRef, ActorSystem, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import rx._

import scala.io.StdIn
import concurrent.duration._

object TestSignal {

  implicit val addMonoid: cats.Monoid[Int] = new cats.Monoid[Int] {
    override def empty = 0
    override def combine(x: Int, y: Int) = x + y
  }

  val hack: Var[Int] = Var(0)

  def behavior(period: FiniteDuration)(implicit owner: rx.Ctx.Owner) =
    RxMonoidSignal(period) { Rx { hack() } }
}

object ListSignal {
  import cats.instances.list._
  val hack: Var[List[Int]] = Var(List.empty)
  def behavior(period: FiniteDuration)(implicit owner: rx.Ctx.Owner) =
    RxMonoidSignal(period) { Rx { hack() } }
}

/////////////
object Blah {
  implicit val rxOwner = rx.Ctx.Owner.voodoo

  val wurch = Actor.immutable[Signal.Raise[Int]] { (_, msg) =>
    println(s"WURCH: $msg")
    Actor.same
  }

  val wurchAlt = Actor.immutable[Signal.Raise[Int]] { (_, msg) =>
    println(s"ALT: $msg")
    Actor.same
  }

  val listWurch = Actor.immutable[Signal.Raise[List[Int]]] { (_, msg) =>
    println(s"NICE: $msg")
    Actor.same
  }

  trait Next { self: HasOwner =>
    val fst: Var[Int] = Var(0)
    val snd: Var[Int] = Var(0)

    val invariant: Rx[List[Int]] = Rx {
      val omg = List(fst(), snd())
      println(s"Ohhhhhh: $omg")
      omg
    }
  }

  class NextRunner(
      fst_sensor: ActorRef[Signal.Command[Int]],
      snd_sensor: ActorRef[Signal.Command[Int]],
      ctx: ActorContext[Nothing]
  )(implicit override val owner: rx.Ctx.Owner)
      extends Next
      with HasOwner {

    //Init thing
    val fst_init: Var[Option[Int]] = Var(None)
    val snd_init: Var[Option[Int]] = Var(None)
    val ready: Rx[Boolean] = Rx {
      (fst_init(), snd_init()) match {
        case (Some(x), Some(y)) =>
          Var.set(fst -> x, snd -> y)
          ctx.spawnAnonymous(Signal.collectToVar(fst, fst_sensor))
          ctx.spawnAnonymous(Signal.collectToVar(snd, snd_sensor))
          cleanup()
          true
        case _ => false
      }
    }

    private def cleanup(): Unit = {
      fst_init.kill()
      snd_init.kill()
      ready.kill()
    }

    def collectToInit[A](sink: Var[Option[A]]): Behavior[Signal.Raise[A]] = Actor.immutable { (_, raised) =>
      sink() = Some(raised.value)
      if(ready.now) Actor.stopped
      else Actor.same
    }

    fst_sensor ! Signal.Register(ctx.spawnAnonymous(collectToInit(fst_init)))
    snd_sensor ! Signal.Register(ctx.spawnAnonymous(collectToInit(snd_init)))
  }

  def main(args: Array[String]): Unit = {
    val root = Actor.deferred[Nothing] { ctx =>
      val test1 = ctx.spawn(TestSignal.behavior(2.seconds), "foo-ooo")
      val test2 = ctx.spawn(TestSignal.behavior(5.seconds), "bar-rrr")
      val test3 = ctx.spawn(ListSignal.behavior(3.seconds), "listo")

      val ref1 = ctx.spawn(wurch, "wurch")
      val ref2 = ctx.spawn(wurchAlt, "alt")
      val ref3 = ctx.spawn(listWurch, "lister")

      test1 ! Signal.Register(ref1)
      test2 ! Signal.Register(ref2)
      test3 ! Signal.Register(ref3)

      TestSignal.hack() = 10
      ListSignal.hack() = List(1, 2, 42)

      val neat = new NextRunner(test1, test2, ctx)
      Actor.empty
    }

    val system = ActorSystem[Nothing](root, "HelloWorld")
    try {
      println("Press ENTER to exit the system")
      StdIn.readLine()
    } catch {
      case e: Exception =>
        println("---------------------> NOOOOOOOOOOOo <----------------")
        println(e.getMessage)
    } finally {
      println("DEATH!")
      system.terminate()
    }
  }
}
