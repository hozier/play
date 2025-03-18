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

  val coreDependencies    = Seq(catsEffect, catsEffectKernel, catsEffectStd)
  val loggingDependencies = Seq(log4catsSlf4j, logbackClassic)
  val testDependencies    = Seq(catsEffectTestingSpecs2, munitCatsEffect, weaverCats, weaverScalacheck)
}
