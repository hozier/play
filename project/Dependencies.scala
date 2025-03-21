import sbt._
import sbt.Keys._

object Versions {
  val catsEffect              = "3.5.3"
  val log4catsSlf4j           = "2.3.0"
  val logbackClassic          = "1.2.11"
  val catsEffectTestingSpecs2 = "1.6.0"
  val munitCatsEffect         = "2.0.0"
  val weaverCats              = "0.7.6"
  val weaverScalacheck        = "0.7.6"
  val googleCloudVision       = "3.57.0"
  val http4s                  = "0.23.12"
  val smithy4s                = "0.17.0"
}

object Dependencies {
  val catsEffect              = "org.typelevel"       %% "cats-effect"                % Versions.catsEffect
  val catsEffectKernel        = "org.typelevel"       %% "cats-effect-kernel"         % Versions.catsEffect
  val catsEffectStd           = "org.typelevel"       %% "cats-effect-std"            % Versions.catsEffect
  val log4catsSlf4j           = "org.typelevel"       %% "log4cats-slf4j"             % Versions.log4catsSlf4j
  val logbackClassic          = "ch.qos.logback"       % "logback-classic"            % Versions.logbackClassic
  val catsEffectTestingSpecs2 = "org.typelevel"       %% "cats-effect-testing-specs2" % Versions.catsEffectTestingSpecs2 % Test
  val munitCatsEffect         = "org.typelevel"       %% "munit-cats-effect"          % Versions.munitCatsEffect         % Test
  val weaverCats              = "com.disneystreaming" %% "weaver-cats"                % Versions.weaverCats              % Test
  val weaverScalacheck        = "com.disneystreaming" %% "weaver-scalacheck"          % Versions.weaverScalacheck        % Test
  val googleCloudVision       = "com.google.cloud"     % "google-cloud-vision"        % Versions.googleCloudVision
  val http4sBlazeServer       = "org.http4s"          %% "http4s-blaze-server"        % Versions.http4s
  val http4sBlazeClient       = "org.http4s"          %% "http4s-blaze-client"        % Versions.http4s
  val http4sDsl               = "org.http4s"          %% "http4s-dsl"                 % Versions.http4s
  val http4sCirce             = "org.http4s"          %% "http4s-circe"               % Versions.http4s

  def smithy4sDependencies(smithy4sVersion: String) = Seq(  
    "com.disneystreaming.smithy4s" %% "smithy4s-core"     % smithy4sVersion, 
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s"   % smithy4sVersion,  
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion,
  )

  val http4sDependencies = Seq(
    http4sBlazeServer,
    http4sBlazeClient,
    http4sDsl,
    http4sCirce
  )
  
  val imageProcessingDependencies = Seq(googleCloudVision)
  val coreDependencies            = Seq(catsEffect, catsEffectKernel, catsEffectStd) ++ imageProcessingDependencies // ++ http4sDependencies
  val loggingDependencies         = Seq(log4catsSlf4j, logbackClassic)
  val testDependencies            = Seq(catsEffectTestingSpecs2, munitCatsEffect, weaverCats, weaverScalacheck)
}
