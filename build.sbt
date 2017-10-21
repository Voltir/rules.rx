val commonSettings = Seq(
    organization := "com.voltir",
    version := "0.1.0-SNAPSHOT",
    parallelExecution in Test := false,
    //fork := true,
    scalacOptions ++= Seq(
      "-language:existentials",
      "-Xfuture",
      "-Ypartial-unification"
    ),
    crossScalaVersions := Seq("2.12.3", "2.11.11"),
    resolvers += "Akka Snapshots" at "https://repo.akka.io/snapshots/",
    addCompilerPlugin(Dependencies.kindProjector)
  )

lazy val root = Project("rules", file("." + "rules")).in(file("."))
  .aggregate(core, aws, quartz)
  .settings(commonSettings: _*)

lazy val core = (project in file("core"))
  .settings(name := "rules-core")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.core,
    libraryDependencies += Dependencies.scalaReflect.value % "provided"
  )

lazy val aws = (project in file("aws"))
  .settings(name := "rules-aws")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.aws
  )
  .dependsOn(core)

lazy val quartz = (project in file("quartz"))
  .settings(name := "rules-quartz")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.quartz
  )
  .dependsOn(core)
