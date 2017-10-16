package rules

import akka.typed.{ActorRef, ActorSystem, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import rx._

import scala.io.StdIn

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

  class ExampleRunner3(fst_sensor: ActorRef[Subscribers.Command[Int]],
                       snd_sensor: ActorRef[Subscribers.Command[Int]],
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
          fst_sensor ! Subscribers.Subscribe(
            ctx.spawn(makeHook2(fst), "fst-hook"))
          snd_sensor ! Subscribers.Subscribe(
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

    fst_sensor ! Subscribers.Subscribe(
      ctx.spawn(BetterInit.init(fst_init), "init-fst-hook"))
    snd_sensor ! Subscribers.Subscribe(
      ctx.spawn(BetterInit.init(snd_init), "init-snd-hook"))
  }

  implicit val rxOwner = rx.Ctx.Owner.voodoo
  def main(args: Array[String]): Unit = {
    val root = Actor.deferred[Nothing] { ctx =>
      //Sensors
      val hackSubs = ctx.spawn(new Subscribers[Int].behavior, "hack-subs")
      val hack =
        ctx.spawn(HackSensor.hack(Subscribers.forward(hackSubs)), "hack-sensor")

      val subs2 = ctx.spawn(new Subscribers[Int].behavior, "hack-subs-2")
      val hack2 =
        ctx.spawn(HackSensor.hack(Subscribers.forward(subs2)), "hack-sensor-2")

      //Rules
      val hook = ctx.spawn(ExampleRunner.hook, "hook")

      hackSubs ! Subscribers.Subscribe(hook)
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
}
