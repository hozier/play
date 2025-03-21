import org.typelevel.sbt.tpolecat.TpolecatPlugin.autoImport._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import smithy4s.codegen.Smithy4sCodegenPlugin
import Dependencies._

val commonSettings =
  Seq(
    organization                := "com.theproductcollectiveco",
    scalaVersion                := "3.4.0",
    parallelExecution in Global := true,
    fork                        := true,
    javaOptions += "-XX:+UseG1GC",
    scalafmtOnCompile           := true, // Enable scalafmt on compile
    useCoursier                 := true,
  )

lazy val root =
  (project in file("."))
    .aggregate(app, tests, smithy)
    .settings(
      name := "play4s"
    )
    .settings(commonSettings)

lazy val app =
  (project in file("app"))
    .dependsOn(smithy)
    .settings(
      name := "play4s-app",
      libraryDependencies ++= coreDependencies ++ loggingDependencies
    )
    .settings(commonSettings)

lazy val tests =
  (project in file("tests"))
    .dependsOn(app, smithy)
    .settings(
      name                     := "play4s-tests",
      libraryDependencies ++= testDependencies,
    )
    .settings(commonSettings)

lazy val smithy =
  (project in file("api"))
    .enablePlugins(Smithy4sCodegenPlugin)
    .settings(
      name := "play4s-smithy",
      libraryDependencies ++= smithy4sDependencies(smithy4sVersion.value)
    )
    .settings(commonSettings)
