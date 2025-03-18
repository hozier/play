import org.typelevel.sbt.tpolecat.TpolecatPlugin.autoImport._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import Dependencies._

val commonSettings =
  Seq(
    organization                := "com.theproductcollectiveco",
    scalaVersion                := "3.4.0",
    parallelExecution in Global := true,
    fork                        := true,
    javaOptions += "-Xmx2G",
    scalafmtOnCompile           := true, // Enable scalafmt on compile
    useCoursier                 := true,
  )

lazy val root =
  (project in file("."))
    .aggregate(core, tests)
    .settings(
      name := "play4s"
    )
    .settings(commonSettings)

lazy val core =
  (project in file("core"))
    .settings(
      name := "play4s-core",
      libraryDependencies ++= Dependencies.coreDependencies ++ Dependencies.loggingDependencies,
    )
    .settings(commonSettings)

lazy val tests =
  (project in file("tests"))
    .dependsOn(core)
    .settings(
      name                     := "play4s-tests",
      Test / scalaSource       := baseDirectory.value / "src" / "scala",
      Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources",
      libraryDependencies ++= Dependencies.testDependencies,
    )
    .settings(commonSettings)
