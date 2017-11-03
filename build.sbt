val commonSettings = Seq(
  organization := "com.voltir",
  version := "0.1.1-SNAPSHOT",
  parallelExecution in Test := false,
  //fork in Test := true,
  scalacOptions ++= Seq(
    "-language:existentials",
    "-language:experimental.macros",
    "-Xfuture",
    "-Ypartial-unification"
  ),
  crossScalaVersions := Seq("2.12.3", "2.11.11"),
  resolvers += "Akka Snapshots" at "https://repo.akka.io/snapshots/",
  addCompilerPlugin(Dependencies.kindProjector),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin(
    "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

lazy val root = Project("rules", file("." + "rules"))
  .in(file("."))
  .aggregate(core, s3, emr, quartz)
  .settings(commonSettings: _*)

lazy val core = (project in file("core"))
  .settings(name := "rules-core")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.core,
    libraryDependencies += Dependencies.scalaReflect.value % "provided"
  )

lazy val s3 = (project in file("s3"))
  .settings(name := "rules-s3")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.aws
  )
  .dependsOn(core)

lazy val emr = (project in file("emr"))
  .settings(name := "rules-emr")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.aws
  )
  .dependsOn(s3, core)

lazy val quartz = (project in file("quartz"))
  .settings(name := "rules-quartz")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.quartz
  )
  .dependsOn(core)
