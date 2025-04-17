package com.theproductcollectiveco.play4s.config

import cats.effect.kernel.Async
import cats.syntax.all.*
import ciris.*

final case class GoogleCloudConfig(
  apiKey: String,
  credentialsFilePath: String,
)

final case class AppConfig(
  googleCloud: GoogleCloudConfig
)

object AppConfig:

  def load[F[_]: Async]: F[AppConfig] =
    (
      env("CREDENTIALS_JSON").as[String],
      env("GOOGLE_APPLICATION_CREDENTIALS").as[String],
    ).parMapN(GoogleCloudConfig.apply)
      .map(AppConfig.apply)
      .load[F]
