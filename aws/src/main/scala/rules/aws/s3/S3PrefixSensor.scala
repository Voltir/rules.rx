package rules.aws.s3

import akka.typed.ActorRef
import akka.typed.scaladsl.Actor

object S3PrefixSensor {

  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String)

  val greeter = Actor.immutable[Greet] { (_, msg) ⇒
    println(s"Hello ${msg.whom}!")
    msg.replyTo ! Greeted(msg.whom)
    Actor.same
  }
}

