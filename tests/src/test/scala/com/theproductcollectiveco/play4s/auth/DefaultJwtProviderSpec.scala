package com.theproductcollectiveco.play4s.auth

import cats.effect.*
import io.circe.syntax.*
import weaver.SimpleIOSuite
import com.theproductcollectiveco.play4s.config.AppConfig
import fs2.io.file.Files
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import java.time.Instant
import com.theproductcollectiveco.play4s.auth.DefaultJwtProvider.*

object DefaultJwtProviderSpec extends SimpleIOSuite {

  test("generateJwt and decodeJwt round-trip correctly") {
    for {
      appConfig       <- AppConfig.load[IO]
      given Async[IO]  = IO.asyncForIO
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
      now              = Instant.now()
      jwtProvider      = DefaultJwtProvider[IO](appConfig, authProvider)
      grant            =
        jwtProvider.createGrant(
          handle = GenericHandle.Username("test-user-id"),
          expiration = Some(now.getEpochSecond + 3600),
          issuedAt = Some(now.getEpochSecond),
          roles = List("user"),
          metadata = Some(Map("env" -> "test")),
          oneTimeUse = false,
        )
      token           <- jwtProvider.generateJwt(grant)
      decodedJson     <- jwtProvider.decodeJwt(token)
      _               <- Logger[IO].info(Map("token" -> token).asJson.noSpaces)
      _               <- Logger[IO].info(Map("decodedJson" -> decodedJson).asJson.noSpaces)
      usernameOpt      =
        decodedJson.hcursor
          .downField("magicLink")
          .downField("payload")
          .downField("genericHandle")
          .downField("Username")
          .downField("value")
          .as[String]
          .toOption
    } yield expect(usernameOpt.contains("test-user-id"))
  }

}
