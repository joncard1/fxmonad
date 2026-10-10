ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / semanticdbEnabled := true
ThisBuild / scalacOptions ++= Seq("-Yretain-trees", "-Wall")

val jfxVersion = "25.0.2-R37"

lazy val core = project
  .in(file("core"))
  .settings(
    name := "fxmonad-core",
    libraryDependencies += "org.scalafx" %% "scalafx" % jfxVersion,
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.2" % Test,
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
    libraryDependencies += "org.testfx" % "testfx-core" % "4.0.18" % Test,
    libraryDependencies += "org.testfx" % "openjfx-monocle" % "21.0.2" % Test,
    Test / fork := true,
    Test / javaOptions ++= Seq(
      "-Dtestfx.headless=true",
      "-Dprism.order=sw",
      "-Dprism.text=t2k"
    )
  )

lazy val macros = project
  .in(file("macros"))
  .dependsOn(core)
  .settings(
    name := "fxmonad-macros",
    libraryDependencies += scalaOrganization.value %% "scala3-compiler" % scalaVersion.value
  )

lazy val testApp = project
  .in(file("testApp"))
  .dependsOn(core, macros)
  .settings(
    name := "fxmonad-test-app",
    libraryDependencies += "org.scalafx" %% "scalafx" % jfxVersion,
    libraryDependencies += "org.hid4java" % "hid4java" % "0.8.0",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.2" % Test
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, macros, testApp)
  .settings(
    name := "fxmonad",
    publish / skip := true,
    Compile / sources := Seq.empty,
    Test / sources := Seq.empty
  )
