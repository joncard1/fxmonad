val scala3Version = "3.8.4"

val jfxVersion = "25.0.2-R37"

lazy val root = project
  .in(file("."))
  .settings(
    name := "fxmonad",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    semanticdbEnabled := true,

    libraryDependencies += "org.scalafx" %% "scalafx" % jfxVersion,
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0",
    libraryDependencies += scalaOrganization.value %% "scala3-compiler" % scalaVersion.value,
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.2" % Test,
    libraryDependencies += "org.testfx" % "testfx-core" % "4.0.18" % Test,
    libraryDependencies += "org.testfx" % "openjfx-monocle" % "21.0.2" % Test
  )

scalacOptions += "-Yretain-trees"

Test / fork := true
Test / javaOptions ++= Seq("-Dtestfx.headless=true", "-Dprism.order=sw", "-Dprism.text=t2k")
Test / scalacOptions += "-Wall"