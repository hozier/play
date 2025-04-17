package com.theproductcollectiveco.play4s.config

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*

final case class GoogleCloudConfig(
  apiKey: Secret[String],
  credentialsFilePath: String,
)

final case class RuntimeConfig(
  appName: String,
  onCI: Boolean,
  version: String,
  gitSha: String,
  buildTimestamp: String,
  organization: String,
)

final case class AppConfig(
  googleCloud: GoogleCloudConfig,
  runtime: RuntimeConfig,
)

object AppConfig:

  def load[F[_]: Async]: F[AppConfig] =
    (
      env("CREDENTIALS_JSON").as[String].map(Secret.apply),
      env("GOOGLE_APPLICATION_CREDENTIALS").as[String],
      env("HOMEBREW_CELLAR").option.map(_.isEmpty),
    ).parMapN: (apiKey, credentialsFilePath, onCI) =>
      AppConfig(
        googleCloud = GoogleCloudConfig(apiKey, credentialsFilePath),
        runtime =
          RuntimeConfig(
            appName = BuildInfo.name,
            onCI = onCI,
            version = BuildInfo.version,
            gitSha = BuildInfo.gitSha,
            buildTimestamp = BuildInfo.buildTimestamp,
            organization = BuildInfo.organization,
          ),
      )
    .load[F]
