package com.theproductcollectiveco.play4s.auth

import cats.effect.kernel.{Async, Resource}
import fs2.io.file.{Files, Path}
import fs2.io.net.tls.TLSContext
import fs2.text
import java.util.Base64
import ciris.Secret
import cats.syntax.all.*
import cats.effect.implicits.*
import com.theproductcollectiveco.play4s.config.AuthConfig
import javax.net.ssl.SSLContext

trait AuthProvider[F[_]] {
  def peekSecret(secret: Secret[String]): F[String]
  def toSanitizedValue(secret: Secret[String]): F[Array[Byte]]
  def storeCredentials(secret: Secret[String], filePath: String)(using Files[F]): Resource[F, Unit]
  def sslContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, SSLContext]
  def tlsContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, TLSContext[F]]
}

object DefaultAuthProvider {

  def apply[F[_]: Async: Files](keyStoreBackend: KeyStoreBackend[F]): AuthProvider[F] =
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
            val sanitizedValue = secret.value.replaceAll("[\\r\\n]", "").filterNot(_.isWhitespace)
            Option
              .when(sanitizedValue.matches("^[A-Za-z0-9+/=]*$"))(sanitizedValue)
              .toRight(new RuntimeException(s"Invalid Base64 input: $sanitizedValue"))
              .flatMap { validValue =>
                Either
                  .catchOnly[IllegalArgumentException](Base64.getDecoder.decode(validValue))
                  .leftMap(e => new RuntimeException(s"Failed to decode Base64: ${e.getMessage}", e))
              }
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
          keystore   <- keyStoreBackend.loadKeyStore(authConfig.credentialsFilePath, authConfig.keyAccessSecret.map(peekSecret).sequence)
          sslContext <- keyStoreBackend.createSSLContext(keystore, authConfig.keyAccessSecret.map(peekSecret).sequence)
        } yield sslContext

      override def tlsContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, TLSContext[F]] =
        sslContextResource(authConfig).map(TLSContext.Builder.forAsync.fromSSLContext)
    }

}
