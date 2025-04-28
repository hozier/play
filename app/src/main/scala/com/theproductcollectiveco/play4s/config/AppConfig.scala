package com.theproductcollectiveco.play4s.config

import ciris.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import cats.effect.kernel.Async
import com.theproductcollectiveco.play4s.internal.meta.health.{RuntimeConfig, ArtifactIdentifiers}

final case class AuthConfig(
  key: Secret[String],
  credentialsFilePath: Option[String] = None,
  keyAccessSecret: Option[Secret[String]] = None,
)

final case class ApiKeyStoreConfig(
  app: AuthConfig,
  googleCloud: AuthConfig,
  keyStoreManagement: AuthConfig,
)

final case class HttpConfig(
  host: Hostname,
  port: Port,
)

final case class AppConfig(
  apiKeyStore: ApiKeyStoreConfig,
  runtime: RuntimeConfig,
  http: HttpConfig,
)

object AppConfig {

  given ConfigDecoder[String, Hostname] = ConfigDecoder[String].mapOption("com.comcast.ip4s.Hostname")(Hostname.fromString)

  given ConfigDecoder[String, Port] = ConfigDecoder[String].mapOption("com.comcast.ip4s.Port")(Port.fromString)

  def load[F[_]: Async]: F[AppConfig] =
    (
      // Defaults are provided for local development and testing purposes
      env("GOOGLE_CLOUD_API_KEY_BASE64").toSecret,
      env("GOOGLE_APPLICATION_CREDENTIALS").option.default("/tmp/path/to/secrets.json".some),
      env("KEYSTORE_BASE64").toSecret,
      env("KEYSTORE_CREDENTIALS").option.default("/tmp/path/to/keystore.p12".some),
      env("KEYSTORE_PASSWORD_BASE64").toSecret,
      env("PLAY4S_API_KEY_BASE64").map(Secret(_)).default(Secret("MTllOTY0MzktZTJiOS00YmM1LWJhMTItNDllZTkxNDI2NjU2Cg==")),
      env("CI").option.map(_.contains("true").some).default(false.some),
      env("DEFAULT_AUTH_JWT").option.map(_.contains("true").some).default(false.some),
      env("APP_NAME").default(BuildInfo.name),
      env("IMAGE_DIGEST").default("sha256:2d551bc2573297d9b9124034f3c89211dfca1b067a055b7b342957815f9673cd"),
      env("SERVICE_BIND_HOST").as[Hostname].default(host"0.0.0.0"),
      env("SERVICE_BIND_PORT").as[Port].default(port"8080"),
    )
      .parMapN:
        (
          googleCloudApiKey,
          googlePath,
          keystore,
          keystorePath,
          keystorePassword,
          appApiKey,
          onCI,
          primaryAuthenticationMechanism,
          appName,
          imageDigest,
          serviceHost,
          servicePort,
        ) =>
          AppConfig(
            ApiKeyStoreConfig(
              app = AuthConfig(appApiKey),
              googleCloud = AuthConfig(googleCloudApiKey, googlePath),
              keyStoreManagement = AuthConfig(keystore, keystorePath, keystorePassword.some),
            ),
            RuntimeConfig(
              appName = appName,
              appVersion = s"${imageDigest.stripPrefix("sha256:").take(12)}-${BuildInfo.gitSha.take(7)}",
              onCI = onCI,
              withJwt = primaryAuthenticationMechanism,
              sbtVersion = BuildInfo.sbtVersion,
              scalaVersion = BuildInfo.scalaVersion,
              organization = BuildInfo.organization,
              artifactIdentifiers =
                ArtifactIdentifiers(
                  gitSha = BuildInfo.gitSha,
                  imageDigest = imageDigest,
                  builtAt =
                    smithy4s.Timestamp.fromInstant(
                      java.time.Instant.parse(BuildInfo.buildTimestamp.trim)
                    ),
                ),
            ),
            HttpConfig(
              host = serviceHost,
              port = servicePort,
            ),
          )
      .load[F]

}

extension [F[_]: Async](configValue: ConfigValue[F, String])

  def toSecret: ConfigValue[F, ciris.Secret[String]] = configValue.map(Secret(_)).default(Secret("eyJkZWZhdWx0IjogImNyZWRlbnRpYWxzIn0=")) // '{"default": "credentials"}'

extension [F[_]: Async](secret: Secret[String])

  def peek: F[String] =
    secret.toSanitizedValue.flatMap { decodedBytes =>
      fs2.Stream
        .emits(decodedBytes)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .compile
        .toList
        .headOption
        .liftTo[F](new IllegalArgumentException("Failed to extract encoded secret"))
    }

  def toSanitizedValue: F[Array[Byte]] =
    Async[F]
      .fromEither {
        val sanitized = secret.value.replaceAll("[\\r\\n]", "").filterNot(_.isWhitespace)
        Option
          .when(sanitized.matches("^[A-Za-z0-9+/=]*$"))(sanitized)
          .toRight(new RuntimeException(s"Invalid Base64 input: $sanitized"))
          .flatMap { validValue =>
            Either
              .catchOnly[IllegalArgumentException](java.util.Base64.getDecoder.decode(validValue))
              .leftMap(e => new RuntimeException(s"Failed to decode Base64: ${e.getMessage}", e))
          }
      }
