package com.theproductcollectiveco.play4s.config

import com.theproductcollectiveco.play4s.internal.meta.health.{RuntimeConfig, ArtifactIdentifiers}
import fs2.io.file.{Files, Path}
import cats.effect.kernel.{Async, Resource}
import cats.syntax.all.*
import cats.effect.implicits.*
import ciris.*

final case class AuthConfig(
  apiKey: Secret[String],
  credentialsFilePath: Option[String] = None,
)

final case class ApiKeyStoreConfig(
  app: AuthConfig,
  googleCloud: AuthConfig,
)

final case class AppConfig(
  apiKeyStore: ApiKeyStoreConfig,
  runtime: RuntimeConfig,
)

object AppConfig {

  def load[F[_]: Async]: Resource[F, AppConfig] =
    (
      // Defaults are provided for local development and testing purposes
      env("CREDENTIALS_JSON").as[String].map(Secret(_)).default(Secret("{'default': 'credentials'}")),
      env("GOOGLE_APPLICATION_CREDENTIALS").option.default("/path/to/secrets.json".some),
      env("PLAY4S_APPLICATION_CREDENTIALS").map(Secret(_)).default(Secret("19e96439-e2b9-4bc5-ba12-49ee91426656")),
      env("HOMEBREW_CELLAR").option.map(_.isEmpty.some), // assert against env var not present within container
      env("APP_NAME").default(BuildInfo.name),
      env("IMAGE_DIGEST").default("sha256:2d551bc2573297d9b9124034f3c89211dfca1b067a055b7b342957815f9673cd"),
    )
      .parMapN: (googleCloudApiKey, credentialsFilePath, appApiKey, onCI, appName, imageDigest) =>
        AppConfig(
          ApiKeyStoreConfig(
            app = AuthConfig(appApiKey),
            googleCloud = AuthConfig(googleCloudApiKey, credentialsFilePath),
          ),
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
