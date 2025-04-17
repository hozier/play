package com.theproductcollectiveco.play4s.config

import com.theproductcollectiveco.play4s.internal.meta.health.{RuntimeConfig, ArtifactIdentifiers}
import fs2.io.file.{Files, Path}
import cats.effect.kernel.{Async, Resource}
import cats.syntax.all.*
import cats.effect.implicits.*
import ciris.*

final case class GoogleCloudConfig(
  apiKey: Secret[String],
  credentialsFilePath: String,
)

final case class AppConfig(
  googleCloud: GoogleCloudConfig,
  runtime: RuntimeConfig,
)

object AppConfig {

  def load[F[_]: Async]: Resource[F, AppConfig] =
    ( // Defaults are provided for local development and testing purposes
      env("CREDENTIALS_JSON").as[String].map(Secret(_)).default(Secret("{'default': 'credentials'}")),
      env("GOOGLE_APPLICATION_CREDENTIALS").as[String].default("/path/to/secrets.json"),
      env("HOMEBREW_CELLAR").option.map(_.isEmpty.some),
      env("APP_NAME").as[String].default(BuildInfo.name),
      env("IMAGE_DIGEST").as[String].default("sha256:2d551bc2573297d9b9124034f3c89211dfca1b067a055b7b342957815f9673cd"),
    )
      .parMapN: (apiKey, credentialsFilePath, onCI, appName, imageDigest) =>
        AppConfig(
          googleCloud = GoogleCloudConfig(apiKey, credentialsFilePath),
          runtime =
            RuntimeConfig(
              appName = appName,
              appVersion = s"${imageDigest.stripPrefix("sha256:").take(12)}-${BuildInfo.gitSha.take(7)}",
              onCI = onCI,
              sbtVersion = BuildInfo.sbtVersion,
              scalaVersion = BuildInfo.scalaVersion,
              organization = BuildInfo.organization,
              artifactIdentifiers =
                ArtifactIdentifiers(
                  gitSha = BuildInfo.gitSha,
                  imageDigest = imageDigest,
                  builtAt =
                    smithy4s.Timestamp.fromInstant:
                      java.time.Instant.parse(BuildInfo.buildTimestamp.trim),
                ),
            ),
        )
      .load[F]
      .toResource

}

extension [F[_]: Async: Files](secret: Secret[String])

  def storeCredentials(filePath: String): Resource[F, Unit] =
    Files[F]
      .createDirectories(Path(filePath).parent.getOrElse(Path(".")))
      .flatMap { _ =>
        fs2.Stream
          .emits(secret.value.getBytes)
          .through(Files[F].writeAll(Path(filePath)))
          .compile
          .drain
      }
      .toResource
