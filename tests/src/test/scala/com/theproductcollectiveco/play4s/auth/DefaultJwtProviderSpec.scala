package com.theproductcollectiveco.play4s.auth

import cats.effect.*
import cats.effect.std.UUIDGen
import com.theproductcollectiveco.play4s.auth.DefaultJwtProvider.*
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.tools.SpecKit.Tasks.{requestTestToken, setupAuthProvider}
import fs2.io.file.Files
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

object DefaultJwtProviderSpec extends SimpleIOSuite {

  test("generateJwt and decodeJwt round-trip correctly") {
    for {
      appConfig       <- AppConfig.load[IO]
      given Logger[IO] = Slf4jLogger.getLogger[IO]
      authProvider    <- setupAuthProvider(appConfig)
      jwtProvider      = DefaultJwtProvider[IO](appConfig, authProvider)
      _               <- authProvider.initializeSecret("jwtSigningSecret", appConfig.apiKeyStore.keyStoreManagement)
      secretWithAlias <- authProvider.retrieveSecret("jwtSigningSecret", appConfig.apiKeyStore.keyStoreManagement)
      token           <- requestTestToken(jwtProvider)
      decodedJson     <- jwtProvider.decodeJwt(token)
      _               <-
        Logger[IO].info:
          Map("jwtSigningSecret" -> secretWithAlias.asJson, "token" -> token.value.asJson, "decodedJson" -> decodedJson).asJson.noSpaces
      username        <-
        IO.fromEither:
          decodedJson.hcursor
            .downField("magicLink")
            .downField("payload")
            .downField("genericHandle")
            .downField("UsernameCase")
            .downField("username")
            .downField("value")
            .as[String]
    } yield expect(username.equals("test-user-id"))
  }

}
