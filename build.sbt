ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / semanticdbEnabled := true
ThisBuild / scalacOptions ++= Seq("-Yretain-trees", "-Wall")

val jfxVersion = "25.0.2-R37"
// Forced version upgrades in response to Dependabot alerts
val fasterxml3Version =
  "3.2.4" // Scaladoc 3.10.0 apparently has dependencies on both fasterxml jackson-core 3 and 2, which seems unwise, but the dependency on 3 is on 3.2.1 and the last LTS version of 3 is 3.1, so this is the latest release.
val fasterxml2Version = "2.21.8" // Latest patch of the most recent LTS.


lazy val core = project
  .in(file("core"))
  .settings(
    name := "fxmonad-core",
    publish / skip := true,
    libraryDependencies += "org.scalafx" %% "scalafx" % jfxVersion,
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.2" % Test,
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
    libraryDependencies += "org.testfx" % "testfx-core" % "4.0.18" % Test,
    libraryDependencies += "org.testfx" % "openjfx-monocle" % "21.0.2" % Test,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-core" % fasterxml3Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-databind" % fasterxml3Version % Configurations.ScalaDocTool,
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
    publish / skip := true,
    libraryDependencies += scalaOrganization.value %% "scala3-compiler" % scalaVersion.value,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-core" % fasterxml3Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-databind" % fasterxml3Version % Configurations.ScalaDocTool
  )

lazy val testApp = project
  .in(file("testApp"))
  .dependsOn(core, macros)
  .settings(
    name := "fxmonad-test-app",
    publish / skip := true,
    libraryDependencies += "org.scalafx" %% "scalafx" % jfxVersion,
    libraryDependencies += "org.hid4java" % "hid4java" % "0.8.0",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.2" % Test,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-core" % fasterxml3Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-databind" % fasterxml3Version % Configurations.ScalaDocTool
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, macros, testApp)
  .settings(
    name := "fxmonad",
    organization := "jackflashtech",
    libraryDependencies += "org.scalafx" %% "scalafx" % jfxVersion,
    libraryDependencies += scalaOrganization.value %% "scala3-compiler" % scalaVersion.value % Provided,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % fasterxml2Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-core" % fasterxml3Version % Configurations.ScalaDocTool,
    libraryDependencies += "tools.jackson.core" % "jackson-databind" % fasterxml3Version % Configurations.ScalaDocTool,
    // The published fxmonad artifact bundles core and macros; testApp is never published.
    Compile / sources := Seq.empty,
    Test / sources := Seq.empty,
    Compile / packageBin / mappings ++= (core / Compile / packageBin / mappings).value,
    Compile / packageBin / mappings ++= (macros / Compile / packageBin / mappings).value,
    Compile / packageSrc / mappings ++= (core / Compile / packageSrc / mappings).value,
    Compile / packageSrc / mappings ++= (macros / Compile / packageSrc / mappings).value
  )
