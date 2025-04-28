package com.theproductcollectiveco.play4s.auth

import cats.effect.*
import com.theproductcollectiveco.play4s.config.AppConfig
import fs2.io.file.Files
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

object DefaultAuthProviderSpec extends SimpleIOSuite {

  test("initializeSecret and retrieveSecret should retrieve existing secret or create one") {
    for {
      appConfig       <- AppConfig.load[IO]
      given Async[IO]  = cats.effect.IO.asyncForIO
      given Logger[IO] = Slf4jLogger.getLogger[IO]
      keyStoreBackend  = DefaultKeyStoreBackend[IO]
      authProvider    <- DefaultAuthProvider[IO](keyStoreBackend)
      _               <-
        authProvider
          .storeCredentials(
            appConfig.apiKeyStore.keyStoreManagement.key,
            appConfig.apiKeyStore.keyStoreManagement.credentialsFilePath.get,
          )
          .use_
      _               <- authProvider.initializeSecret("jwtSigningSecret", appConfig.apiKeyStore.keyStoreManagement)
      secretWithAlias <- authProvider.retrieveSecret("jwtSigningSecret", appConfig.apiKeyStore.keyStoreManagement)
    } yield expect(secretWithAlias.nonEmpty)
  }

}
