package com.theproductcollectiveco.play4s.auth

import cats.effect.Ref
import cats.effect.implicits.*
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.syntax.all.*
import ciris.Secret
import com.theproductcollectiveco.play4s.config.{toSanitizedValue, AuthConfig}
import com.theproductcollectiveco.play4s.internal.auth.{Alias, AuthProcessingError}
import fs2.io.file.{Files, Path}
import fs2.io.net.tls.TLSContext
import javax.net.ssl.SSLContext

trait AuthProvider[F[_]] {
  def retrieveSecret(alias: Alias, authConfig: AuthConfig): F[Secret[String]]
  def initializeSecret(alias: Alias, authConfig: AuthConfig, providedSecret: Option[String] = None): F[Unit]
  def removeSecret(alias: Alias, authConfig: AuthConfig): F[Unit]
  def storeCredentials(secret: Secret[String], filePath: String)(using Files[F]): Resource[F, Unit]
  def sslContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, SSLContext]
  def tlsContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, TLSContext[F]]
}

object DefaultAuthProvider {

  def apply[F[_]: Async](keyStoreBackend: KeyStoreBackend[F]): F[AuthProvider[F]] =
    for {
      loadedKeyStoreRef <- Ref.of[F, Option[LoadedKeyStore[F]]](None)
    } yield new AuthProvider[F] {

      private def getOrLoadKeyStore(authConfig: AuthConfig): F[LoadedKeyStore[F]] =
        loadedKeyStoreRef.get.flatMap {
          case Some(loaded) => loaded.pure
          case None         => keyStoreBackend.load(authConfig).allocated.flatMap { case (loaded, _) => loadedKeyStoreRef.set(Some(loaded)) *> loaded.pure }
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
        getOrLoadKeyStore(authConfig).flatMap(_.createSSLContext).toResource

      override def tlsContextResource(authConfig: AuthConfig)(using Files[F]): Resource[F, TLSContext[F]] =
        sslContextResource(authConfig).map(TLSContext.Builder.forAsync.fromSSLContext)

      override def retrieveSecret(alias: Alias, authConfig: AuthConfig): F[Secret[String]] =
        getOrLoadKeyStore(authConfig)
          .flatMap(_.retrieve(alias))
          .map(_.map(Secret(_)))
          .flatMap(_.liftTo[F](AuthProcessingError(s"Secret with alias '$alias' not found in keystore")))

      override def removeSecret(alias: Alias, authConfig: AuthConfig): F[Unit] =
        getOrLoadKeyStore(authConfig)
          .flatMap(_.delete(alias))

      override def initializeSecret(alias: Alias, authConfig: AuthConfig, providedSecret: Option[String] = None): F[Unit] =
        getOrLoadKeyStore(authConfig).flatMap { loaded =>
          SecureRandom.javaSecuritySecureRandom[F].flatMap { security =>
            given SecureRandom[F] = security
            UUIDGen.fromSecureRandom.randomUUID.map(_.toString).flatMap { uuid =>
              loaded.retrieve(alias).flatMap { case Some(_) | None => loaded.store(alias, providedSecret.getOrElse(uuid)) }
            }
          }
        }

    }

}
