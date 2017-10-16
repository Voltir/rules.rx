import sbt._
import sbt.Keys._

object Dependencies {
  lazy val akkaVersion = "2.5.6"
  lazy val akkaTypedVersion = "2.5.7-M1"
  lazy val scalaRxVersion = "0.3.2"
  lazy val awsVersion = "1.11.213"

  lazy val kindProjector = "org.spire-math" %% "kind-projector" % "0.9.3"

  import Def.setting

  lazy val common = Seq(
    "com.typesafe.akka" %% "akka-typed" % akkaTypedVersion,
    "com.lihaoyi" %% "scalarx" % scalaRxVersion,
    "org.scalatest" %% "scalatest" % "3.0.4" % "test"
  )

  def scalaReflect = setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)

  lazy val core = common ++ Seq(
    "com.typesafe.akka" %% "akka-typed-testkit" % akkaTypedVersion  % "test"
  )

  lazy val aws = common ++ Seq(
    "com.amazonaws" % "aws-java-sdk" % awsVersion,
    "com.amazonaws" % "aws-java-sdk-s3" % awsVersion
  )

  lazy val quartz = common ++ Seq(
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.6.1-akka-2.5.x"
  )
}
