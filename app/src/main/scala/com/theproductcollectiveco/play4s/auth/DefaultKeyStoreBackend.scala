package com.theproductcollectiveco.play4s.auth

import cats.effect.{Async, Resource, Sync}
import cats.effect.implicits.*
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.config.{peek, AuthConfig}
import com.theproductcollectiveco.play4s.internal.auth.Alias
import fs2.io.file.{Files, Path}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import java.security.KeyStore

trait KeyStoreBackend[F[_]] {
  def load(authConfig: AuthConfig): Resource[F, LoadedKeyStore[F]]
}

trait LoadedKeyStore[F[_]] {
  def retrieve(alias: Alias): F[Option[String]]
  def store(alias: Alias, secret: String): F[Unit]
  def delete(alias: Alias): F[Unit]
  def createSSLContext: F[SSLContext]
}

object DefaultKeyStoreBackend {

  def apply[F[_]: Async: Files]: KeyStoreBackend[F] =
    new KeyStoreBackend[F] {

      override def load(authConfig: AuthConfig): Resource[F, LoadedKeyStore[F]] = loadKeyStore(authConfig).toResource

      private def loadKeyStore(authConfig: AuthConfig): F[LoadedKeyStore[F]] =
        for {
          credentialsPath <-
            authConfig.credentialsFilePath
              .liftTo[F](new IllegalArgumentException("Missing credentials file path"))
              .map(Path(_))
          password        <-
            authConfig.keyAccessSecret
              .liftTo[F](new IllegalArgumentException("Missing key access secret"))
              .flatMap(_.peek)
          keystore        <- DefaultLoadedKeyStore.load(credentialsPath, password)
        } yield DefaultLoadedKeyStore[F](keystore, password)

      object DefaultLoadedKeyStore {

        def apply[F[_]: Async: Files](keystore: KeyStore, password: String): LoadedKeyStore[F] =
          new LoadedKeyStore[F] {

            private def protection = new KeyStore.PasswordProtection(password.toCharArray)

            override def retrieve(alias: Alias): F[Option[String]] =
              Sync[F].delay {
                Option(keystore.getEntry(alias.value, protection)) match {
                  case Some(entry: KeyStore.SecretKeyEntry) => Some(new String(entry.getSecretKey.getEncoded))
                  case _                                    => None
                }
              }

            override def store(alias: Alias, secret: String): F[Unit] =
              Sync[F].delay {
                val secretKey = new javax.crypto.spec.SecretKeySpec(secret.replaceAll("[\\r\\n]", "").getBytes("UTF-8"), "HmacSHA256")
                val entry     = new KeyStore.SecretKeyEntry(secretKey)
                keystore.setEntry(alias.value, entry, protection)
              }

            override def delete(alias: Alias): F[Unit] =
              Sync[F].delay:
                keystore.deleteEntry(alias.value)

            override def createSSLContext: F[SSLContext] =
              Async[F].delay {
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
                kmf.init(keystore, password.toCharArray)

                val sslContext                                               = SSLContext.getInstance("TLS")
                val trustManagers: Option[Array[javax.net.ssl.TrustManager]] = None
                val secureRandom: Option[java.security.SecureRandom]         = None

                sslContext.init(
                  kmf.getKeyManagers,
                  trustManagers.orNull,
                  secureRandom.orNull,
                )
                sslContext
              }
          }

        def load[F[_]: Async: Files](path: Path, password: String): F[KeyStore] =
          Files[F]
            .readAll(path)
            .compile
            .to(Array)
            .flatMap { bytes =>
              Async[F].blocking {
                val ks = KeyStore.getInstance("PKCS12")
                val is = new java.io.ByteArrayInputStream(bytes)
                try {
                  ks.load(is, password.toCharArray)
                  ks
                } finally is.close()
              }
            }
      }
    }

}
