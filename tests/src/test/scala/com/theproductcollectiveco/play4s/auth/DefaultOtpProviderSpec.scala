package com.theproductcollectiveco.play4s.auth

import cats.effect.*
import cats.effect.std.Supervisor
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.internal.auth.{Alias, *}
import com.theproductcollectiveco.play4s.internal.auth.Contact.EmailAddressCase
import com.theproductcollectiveco.play4s.tools.SpecKit.Tasks.setupAuthProvider
import fs2.io.file.Files
import io.circe.Encoder
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

object DefaultOtpProviderSpec extends SimpleIOSuite {

  test("initializeOtp and verifyOtpEligibility round-trip correctly") {
    for {
      appConfig            <- AppConfig.load[IO]
      given Logger[IO]      = Slf4jLogger.getLogger[IO]
      given Supervisor[IO] <- Supervisor[IO].use(IO(_))
      authProvider         <- setupAuthProvider(appConfig)
      otpProvider           = DefaultOtpProvider[IO](appConfig, authProvider, Clock[IO])
      requester             = GenericHandle.contact(Contact.emailAddress(EmailAddress("test-user-id@icloud.com")))
      _                    <- authProvider.initializeSecret(Alias("jwtSigningSecret"), appConfig.apiKeyStore.keyStoreManagement)
      secretWithAlias      <- authProvider.retrieveSecret(Alias("jwtSigningSecret"), appConfig.apiKeyStore.keyStoreManagement)
      otp                  <- otpProvider.initializeOtp(requester)
      isEligible           <- otpProvider.verifyOtpEligibility(requester, otp)
      _                    <-
        Logger[IO].info:
          Map("jwtSigningSecret" -> secretWithAlias.asJson, "requester" -> requester.asJson, "otp" -> otp.asJson).asJson.noSpaces
    } yield expect(isEligible)
  }

}
