package com.theproductcollectiveco.play4s.config

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*

final case class GoogleCloudConfig(
  apiKey: String,
  credentialsFilePath: String,
)

final case class RuntimeConfig(
  onCI: Boolean
)

final case class AppConfig(
  googleCloud: GoogleCloudConfig,
  runtime: RuntimeConfig,
)

object AppConfig:

  def load[F[_]: Async]: F[AppConfig] =
    (
      env("CREDENTIALS_JSON").as[String],
      env("GOOGLE_APPLICATION_CREDENTIALS").as[String],
      env("HOMEBREW_CELLAR").option.map(_.isEmpty),
    ).parMapN: (apiKey, credentialsFilePath, onCI) =>
      AppConfig(
        googleCloud = GoogleCloudConfig(apiKey, credentialsFilePath),
        runtime = RuntimeConfig(onCI),
      )
    .load[F]
