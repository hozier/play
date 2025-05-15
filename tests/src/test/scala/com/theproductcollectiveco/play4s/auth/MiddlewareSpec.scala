package com.theproductcollectiveco.play4s.auth

import cats.effect.IO
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.Middleware.secureRoutes
import com.theproductcollectiveco.play4s.api.HealthService
import com.theproductcollectiveco.play4s.auth.*
import com.theproductcollectiveco.play4s.auth.DefaultJwtProvider.*
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.internal.auth.Alias
import com.theproductcollectiveco.play4s.tools.SpecKit.Tasks.{requestTestToken, setupAuthProvider}
import fs2.io.file.Files
import org.http4s.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite

object MiddlewareSpec extends SimpleIOSuite {

  test("secureRoutes accepts valid JWT and rejects invalid") {
    for {
      appConfig            <- AppConfig.load[IO]
      given Logger[IO]      = Slf4jLogger.getLogger[IO]
      given AppConfig       = appConfig
      authProvider         <- setupAuthProvider(appConfig)
      given JwtProvider[IO] = DefaultJwtProvider[IO](appConfig, authProvider)
      _                    <- authProvider.initializeSecret(Alias("jwtSigningSecret"), appConfig.apiKeyStore.keyStoreManagement)
      token                <- requestTestToken(summon[JwtProvider[IO]])
      healthService         = HealthService.make[IO]
      securedApp            = healthService.secureRoutes.orNotFound
      goodRequest           =
        Request[IO](Method.GET, uri"/internal/meta/version")
          .putHeaders:
            Authorization(Credentials.Token(AuthScheme.Bearer, token.value))
      badRequest            =
        Request[IO](Method.GET, uri"/internal/meta/version")
          .putHeaders:
            Authorization(Credentials.Token(AuthScheme.Bearer, "invalid-token"))
      goodResponse         <- securedApp.run(goodRequest)
      badResponse          <- securedApp.run(badRequest)
      body                 <- goodResponse.as[String]
    } yield expect(goodResponse.status == Status.Ok) and
      expect(body.nonEmpty) and
      expect(badResponse.status == Status.Forbidden)
  }

}
