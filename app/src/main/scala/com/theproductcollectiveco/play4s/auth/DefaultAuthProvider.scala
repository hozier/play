package com.theproductcollectiveco.play4s.auth

import cats.effect.kernel.{Async, Resource}
import fs2.io.file.{Files, Path}
import fs2.io.net.tls.TLSContext
import fs2.text
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import java.util.Base64
import ciris.Secret
import cats.syntax.all.*
import cats.effect.implicits.*
import com.theproductcollectiveco.play4s.config.AuthConfig

trait AuthProvider[F[_]] {
  def peekSecret(secret: Secret[String]): F[String]
  def toSanitizedValue(secret: Secret[String]): F[Array[Byte]]
  def storeCredentials(secret: Secret[String], filePath: String)(using Files[F]): Resource[F, Unit]
  def sslContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, SSLContext]
  def tlsContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, TLSContext[F]]
}

object DefaultAuthProvider {

  def apply[F[_]: Async]: AuthProvider[F] =
    new AuthProvider[F] {
      override def peekSecret(secret: Secret[String]): F[String] =
        toSanitizedValue(secret).flatMap { decodedBytes =>
          fs2.Stream
            .emits(decodedBytes)
            .through(text.utf8.decode)
            .through(text.lines)
            .compile
            .toList
            .headOption
            .liftTo[F](new IllegalArgumentException("Failed to extract encoded secret"))
        }

      override def toSanitizedValue(secret: Secret[String]): F[Array[Byte]] =
        Async[F]
          .fromEither {
            val sanitizedValue = secret.value.filterNot(_.isWhitespace)
            Either
              .catchOnly[IllegalArgumentException](Base64.getDecoder.decode(sanitizedValue))
              .leftMap(e => new RuntimeException(s"Failed to decode Base64: ${e.getMessage}", e))
          }

      override def storeCredentials(secret: Secret[String], filePath: String)(using Files[F]): Resource[F, Unit] =
        Files[F]
          .createDirectories(Path(filePath).parent.getOrElse(Path(".")))
          .flatMap { _ =>
            toSanitizedValue(secret)
              .flatMap { decodedBytes =>
                fs2.Stream
                  .emits(decodedBytes)
                  .through(Files[F].writeAll(Path(filePath)))
                  .compile
                  .drain
              }
          }
          .toResource

      override def sslContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, SSLContext] =
        for {
          keystore   <- loadKeyStore(authConfig)
          sslContext <- createSSLContext(authConfig, keystore)
        } yield sslContext

      override def tlsContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, TLSContext[F]] =
        for {
          keystore   <- loadKeyStore(authConfig)
          sslContext <- createSSLContext(authConfig, keystore)
          tlsContext  = TLSContext.Builder.forAsync.fromSSLContext(sslContext)
        } yield tlsContext

      private def loadKeyStore(authConfig: AuthConfig)(using Files[F]): Resource[F, KeyStore] =
        for {
          filePath <-
            authConfig.credentialsFilePath
              .liftTo[F](new IllegalArgumentException("Keystore file path is missing"))
              .toResource
          password <-
            authConfig.keyAccessSecret
              .map(peekSecret)
              .getOrElse(new IllegalArgumentException("Keystore password is missing").raiseError)
              .toResource
          keystore <- Async[F].delay(KeyStore.getInstance("PKCS12")).toResource
          _        <-
            Files[F]
              .readAll(Path(filePath))
              .compile
              .to(Array)
              .flatMap { bytes =>
                Async[F].delay {
                  keystore.load(new java.io.ByteArrayInputStream(bytes), password.toCharArray)
                }
              }
              .toResource
        } yield keystore

      private def createSSLContext(authConfig: AuthConfig, keystore: KeyStore): Resource[F, SSLContext] =
        for {
          password   <-
            authConfig.keyAccessSecret
              .map(peekSecret)
              .getOrElse(new IllegalArgumentException("Keystore password is missing").raiseError)
              .toResource
          sslContext <-
            Async[F].delay {
              val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
              keyManagerFactory.init(keystore, password.toCharArray)

              val sslContext                                               = SSLContext.getInstance("TLS")
              val trustManagers: Option[Array[javax.net.ssl.TrustManager]] = None
              val secureRandom: Option[java.security.SecureRandom]         = None

              sslContext.init(
                keyManagerFactory.getKeyManagers,
                trustManagers.orNull,
                secureRandom.orNull,
              )
              sslContext
            }.toResource
        } yield sslContext
    }

}
