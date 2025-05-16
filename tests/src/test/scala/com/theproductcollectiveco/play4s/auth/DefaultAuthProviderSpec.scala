package com.theproductcollectiveco.play4s.auth

import cats.effect.*
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.internal.auth.Alias
import com.theproductcollectiveco.play4s.tools.SpecKit.Tasks.setupAuthProvider
import fs2.io.file.Files
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

object DefaultAuthProviderSpec extends SimpleIOSuite {

  test("initializeSecret and retrieveSecret should retrieve existing secret or create one") {
    for {
      appConfig           <- AppConfig.load[IO]
      given Logger[IO]     = Slf4jLogger.getLogger[IO]
      authProvider        <- setupAuthProvider(appConfig)
      _                   <- authProvider.initializeSecret(Alias("jwtSigningSecret"), appConfig.apiKeyStore.keyStoreManagement)
      symmetricSigningKey <- authProvider.retrieveSecret(Alias("jwtSigningSecret"), appConfig.apiKeyStore.keyStoreManagement)
    } yield expect(symmetricSigningKey.value.nonEmpty)
  }

}
