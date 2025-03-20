import org.typelevel.sbt.tpolecat.TpolecatPlugin.autoImport._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
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
    .aggregate(app, tests)
    .settings(
      name := "play4s"
    )
    .settings(commonSettings)

lazy val app =
  (project in file("app"))
    .settings(
      name := "play4s-app",
      libraryDependencies ++= Dependencies.coreDependencies ++ Dependencies.loggingDependencies ++ Dependencies.imageProcessingDependencies,
    )
    .settings(commonSettings)

lazy val tests =
  (project in file("tests"))
    .dependsOn(app)
    .settings(
      name                     := "play4s-tests",
      Test / scalaSource       := baseDirectory.value / "src" / "scala",
      Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources",
      libraryDependencies ++= Dependencies.testDependencies,
    )
    .settings(commonSettings)
