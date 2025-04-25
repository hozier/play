package com.theproductcollectiveco.play4s.auth

import cats.effect.{Async, Sync, Resource}
import cats.effect.implicits.*
import cats.syntax.all.*
import fs2.io.file.{Files, Path}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import java.security.KeyStore

trait KeyStoreBackend[F[_]] {
  def loadKeyStore(credentialsFilePath: Option[String], keyAccessSecret: F[Option[String]]): Resource[F, KeyStore]
  def createSSLContext(keystore: KeyStore, keyAccessSecret: F[Option[String]]): Resource[F, SSLContext]
  def retrieve(keystore: KeyStore, alias: String): F[Option[String]]
  def store(keystore: KeyStore, alias: String, secret: String): F[Unit]
}

object DefaultKeyStoreBackend {

  def apply[F[_]: Async: Files]: KeyStoreBackend[F] =
    new KeyStoreBackend[F] {

      override def loadKeyStore(credentialsFilePath: Option[String], keyAccessSecret: F[Option[String]]): Resource[F, KeyStore] =
        for {
          filePath <-
            credentialsFilePath
              .liftTo[F](new IllegalArgumentException("Keystore file path is missing"))
              .toResource
          password <- keyAccessSecret.flatMap(_.liftTo[F](new IllegalArgumentException("Keystore password is missing"))).toResource
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

      override def createSSLContext(keystore: KeyStore, keyAccessSecret: F[Option[String]]): Resource[F, SSLContext] =
        for {
          password   <- keyAccessSecret.flatMap(_.liftTo[F](new IllegalArgumentException("Keystore password is missing"))).toResource
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

      override def retrieve(keystore: KeyStore, alias: String): F[Option[String]] =
        Sync[F].delay {
          Option(keystore.getEntry(alias, null)) match {
            case Some(entry: KeyStore.SecretKeyEntry) => Option(new String(entry.getSecretKey.getEncoded)) // Decode the secret key
            case _                                    => None
          }
        }

      override def store(keystore: KeyStore, alias: String, secret: String): F[Unit] =
        Sync[F].delay {
          val secretKey = new javax.crypto.spec.SecretKeySpec(secret.getBytes, "AES")
          val entry     = new KeyStore.SecretKeyEntry(secretKey)
          keystore.setEntry(alias, entry, null)
        }
    }

}
