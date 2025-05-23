import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import smithy4s.codegen.Smithy4sCodegenPlugin
import com.typesafe.sbt.packager.docker.Cmd
import Dependencies._

val commonSettings =
  Seq(
    organization                     := "com.theproductcollectiveco",
    scalaVersion                     := "3.4.0",
    semanticdbVersion                := scalafixSemanticdb.revision,
    semanticdbEnabled                := true,
    Compile / parallelExecution      := true,
    fork                             := true,
    javaOptions ++= Seq(
      "-Xms12G",
      "-Xmx12G",
      "-XX:+UseParallelGC",
      "-Dcats.effect.threads.bounded=16",
      "-Dcats.effect.threads.blocking=32",
    ),
    scalacOptions ++= Seq("-Xmax-inlines", "64"),
    useCoursier                      := true,
    scalafixOnCompile                := true,
    scalafmtOnCompile                := true,
    Compile / run / fork             := true,
    Compile / concurrentRestrictions := Seq(Tags.limitAll(4)),
  )

lazy val root =
  (project in file("."))
    .aggregate(app, tests, smithy)
    .settings(name := "play4s")
    .settings(commonSettings)
    .enablePlugins(TpolecatPlugin, BuildInfoPlugin)

lazy val app =
  (project in file("app"))
    .dependsOn(smithy)
    .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)
    .settings(
      name                := "play4s-app",
      version             := "0.1.0-SNAPSHOT",
      libraryDependencies ++= coreDependencies ++ loggingDependencies,
      dockerBaseImage     := "openjdk:21-slim",
      dockerExposedPorts  := Seq(8080),
      dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
      dockerCommands ++= Seq(
        Cmd("USER", "root"),         // Switch to root user
        Cmd("RUN", "apt-get update && apt-get install -y curl && apt-get install -y jq"),
        Cmd("USER", "demiourgos728"), // Switch back to the non-root user
      ),
      dockerAlias         := DockerAlias(
        sys.env.get("REGISTRY"),
        Some("theproductcollectiveco"),
        "app-play4s-service-repository",
        Some(sys.env.getOrElse("GIT_SHA", "latest")),
      ),
      Compile / mainClass := Some("com.theproductcollectiveco.play4s.MainApp"),
      dockerEntrypoint    := Seq("/opt/docker/bin/play4s-app"),
      buildInfoPackage    := "com.theproductcollectiveco.play4s.config",
      buildInfoKeys       := Seq[BuildInfoKey](
        name,
        organization,
        version,
        scalaVersion,
        sbtVersion,
        BuildInfoKey.action("gitSha")(sys.env.getOrElse("GIT_SHA", "latest")),
        BuildInfoKey.action("buildTimestamp")(java.time.Instant.now.toString),
      ),
    )
    .settings(commonSettings)

lazy val tests =
  (project in file("tests"))
    .dependsOn(app, smithy)
    .settings(
      name           := "play4s-tests",
      publish / skip := true,
      libraryDependencies ++= testDependencies,
    )
    .settings(commonSettings)

lazy val smithy =
  (project in file("api"))
    .enablePlugins(Smithy4sCodegenPlugin)
    .settings(
      name := "play4s-smithy",
      libraryDependencies ++= smithy4sDependencies(smithy4sVersion.value),
    )
    .settings(commonSettings)
