package com.theproductcollectiveco.play4s.auth

import cats.effect.{IO, Async}
import weaver.SimpleIOSuite
import com.theproductcollectiveco.play4s.auth.*
import com.theproductcollectiveco.play4s.api.HealthService
import com.theproductcollectiveco.play4s.auth.DefaultJwtProvider.*
import com.theproductcollectiveco.play4s.Middleware.secureRoutes
import com.theproductcollectiveco.play4s.config.AppConfig
import org.http4s.*
import org.http4s.implicits.*
import cats.effect.std.UUIDGen
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import org.typelevel.ci.CIStringSyntax
import fs2.io.file.Files
import java.time.Instant

object MiddlewareSpec extends SimpleIOSuite {

  test("secureRoutes accepts valid JWT and rejects invalid") {
    for {
      appConfig            <- AppConfig.load[IO]
      given Async[IO]       = IO.asyncForIO
      given Logger[IO]      = Slf4jLogger.getLogger[IO]
      given AppConfig       = appConfig
      keyStoreBackend       = DefaultKeyStoreBackend[IO]
      authProvider         <- DefaultAuthProvider[IO](keyStoreBackend)
      _                    <-
        authProvider
          .storeCredentials(appConfig.apiKeyStore.keyStoreManagement.key, appConfig.apiKeyStore.keyStoreManagement.credentialsFilePath.get)
          .use_
      _                    <- authProvider.initializeSecret("jwtSigningSecret", appConfig.apiKeyStore.keyStoreManagement)
      jwtProvider           = DefaultJwtProvider[IO](appConfig, authProvider)
      now                   = Instant.now()
      uuid                 <- UUIDGen.fromSync[IO](Async[IO]).randomUUID.map(_.toString)
      token                <-
        jwtProvider.generateJwt(
          handle = GenericHandle.Username("test-user-id"),
          expiration = Some(now.getEpochSecond + 3600),
          issuedAt = Some(now.getEpochSecond),
          tokenId = Some(uuid),
          roles = List("user"),
          metadata = Some(Map("env" -> "test")),
          oneTimeUse = false,
          issuer = Some("app.play4s-service.io"),
        )
      healthService         = HealthService.make[IO]
      given JwtProvider[IO] = jwtProvider
      securedApp            = healthService.secureRoutes.orNotFound
      goodRequest           =
        Request[IO](Method.GET, uri"/internal/meta/version")
          .putHeaders(Header.Raw(ci"Authorization", s"Bearer $token"))
      badRequest            =
        Request[IO](Method.GET, uri"/internal/meta/version")
          .putHeaders(Header.Raw(ci"Authorization", "Bearer invalid-token"))
      goodResponse         <- securedApp.run(goodRequest)
      badResponse          <- securedApp.run(badRequest)
      body                 <- goodResponse.as[String]
    } yield expect(goodResponse.status == Status.Ok) and
      expect(body.nonEmpty) and
      expect(badResponse.status == Status.Forbidden)
  }

}
