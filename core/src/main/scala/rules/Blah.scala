package rules

import akka.typed.{ActorRef, ActorSystem, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import rx._

import scala.io.StdIn
import concurrent.duration._

trait Example2 extends Rule[List[Int]] {
  val fst: Var[Int] = Var(0)
  val snd: Var[Int] = Var(0)

  override val invariant: Rx[List[Int]] = Rx {
    val omg = List(fst(), snd())
    println(s"Ohhhhhh: $omg")
    omg
  }
}

////////////
object HackSensor {
  import concurrent.duration._
  sealed trait Command

  case class Signal(next: Int = 42) extends Command

  object Tick extends Command

  def hack(notify: Behavior[Int]) = {
    Actor.deferred[Command] { ctx =>
      val ref = ctx.spawn(notify, "notifier")
      Actor.withTimers[Command] { timers =>
        timers.startPeriodicTimer(10, Tick, 5.seconds)
        running(timers, ref, signal = 0)
      }
    }
  }

  private def running(timers: TimerScheduler[Command],
                      ref: ActorRef[Int],
                      signal: Int): Behavior[Command] = Actor.immutable {
    (_, msg) =>
      msg match {
        case Signal(x) =>
          ref ! x
          running(timers, ref, x)
        case Tick =>
          ref ! signal
          Actor.same
      }
  }
}

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

  object ExampleRunner {
    val src1: Var[List[String]] = Var(List.empty)
    val src2: Var[Int] = Var(0)

    val invarient = Rx {
      println(s"Nooice: ${src2()}")
      src1().map(_.toInt).filter(_ < src2())
    }

    val hook = Actor.immutable[Int] { (_, v) =>
      src2() = v
      Actor.same
    }
  }

  def makeHook2[A](target: Var[A]): Behavior[A] = Actor.immutable { (_, v) =>
    target() = v
    Actor.same
  }

  object BetterInit {
    def init[A](i: Var[Option[A]]): Behavior[A] = Actor.immutable[A] { (_, v) =>
      if (i.now.isEmpty) {
        i() = Some(v)
        Actor.stopped
      } else {
        Actor.same
      }
    }
  }

  class ExampleRunner3(fst_sensor: ActorRef[Subscribersz.Command[Int]],
                       snd_sensor: ActorRef[Subscribersz.Command[Int]],
                       ctx: ActorContext[Nothing])(
      implicit override val rxOwnerContext: rx.Ctx.Owner)
      extends Example2 {

    //Init thing
    val fst_init: Var[Option[Int]] = Var(None)
    val snd_init: Var[Option[Int]] = Var(None)
    val action: Rx[Unit] = Rx {
      (fst_init(), snd_init()) match {
        case (Some(x), Some(y)) =>
          println("IIIIIIIIIIIIIINNNNNNNTTTDDDDDDDDDDDDDDDDDDDDDDD")
          Var.set(fst -> x, snd -> y)
          fst_sensor ! Subscribersz.Subscribe(
            ctx.spawn(makeHook2(fst), "fst-hook"))
          snd_sensor ! Subscribersz.Subscribe(
            ctx.spawn(makeHook2(snd), "snd-hook"))
          cleanup()
        case _ => ()
      }
    }

    private def cleanup(): Unit = {
      fst_init.kill()
      snd_init.kill()
      action.kill()
    }

    fst_sensor ! Subscribersz.Subscribe(
      ctx.spawn(BetterInit.init(fst_init), "init-fst-hook"))
    snd_sensor ! Subscribersz.Subscribe(
      ctx.spawn(BetterInit.init(snd_init), "init-snd-hook"))
  }

  implicit val rxOwner = rx.Ctx.Owner.voodoo
  def mainz(args: Array[String]): Unit = {
    val root = Actor.deferred[Nothing] { ctx =>
      //Sensors
      val hackSubs = ctx.spawn(new Subscribersz[Int].behavior, "hack-subs")
      val hack =
        ctx.spawn(HackSensor.hack(Subscribersz.forward(hackSubs)), "hack-sensor")

      val subs2 = ctx.spawn(new Subscribersz[Int].behavior, "hack-subs-2")
      val hack2 =
        ctx.spawn(HackSensor.hack(Subscribersz.forward(subs2)), "hack-sensor-2")

      //Rules
      val hook = ctx.spawn(ExampleRunner.hook, "hook")

      hackSubs ! Subscribersz.Subscribe(hook)
      hack ! HackSensor.Signal()

      ////////////////
      new ExampleRunner3(hackSubs, subs2, ctx)

      hack ! HackSensor.Signal(10)
      hack2 ! HackSensor.Signal(100)
      hack2 ! HackSensor.Signal(100)
      hack ! HackSensor.Signal(500)
      println("??????????????????????")
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

  trait HasOwner {
    implicit def owner: Ctx.Owner
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
