package com.theproductcollectiveco.play4s

import cats.effect.{Clock, IO, Resource, ResourceApp}
import cats.effect.IO.asyncForIO
import cats.effect.implicits.*
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.Middleware.{routes, secureRoutes}
import com.theproductcollectiveco.play4s.Play4sApi
import com.theproductcollectiveco.play4s.api.{AuthService, HealthService, Play4sService}
import com.theproductcollectiveco.play4s.auth.{AuthProvider, DefaultAuthProvider, DefaultJwtProvider, DefaultKeyStoreBackend, JwtProvider}
import com.theproductcollectiveco.play4s.auth.DefaultJwtProvider.*
import com.theproductcollectiveco.play4s.config.{ApiKeyStoreConfig, AppConfig, AuthConfig}
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{GoogleCloudClient, TraceClient}
import com.theproductcollectiveco.play4s.internal.auth.ServiceAuthApi
import com.theproductcollectiveco.play4s.internal.meta.health.ServiceMetaApi
import fs2.io.file.Files
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import smithy4s.http4s.swagger.docs

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] =
    for {
      given AppConfig        <- AppConfig.load[IO].toResource
      given Logger[IO]       <- Slf4jLogger.create[IO].toResource
      AppConfig(
        ApiKeyStoreConfig(_, AuthConfig(googleCloudApiKey, googlePath, _), AuthConfig(keystore, keystorePath, _)),
        _,
        _,
      )                       = summon[AppConfig]
      authProvider           <- DefaultKeyStoreBackend[IO].pure.flatMap(DefaultAuthProvider[IO]).toResource
      _                      <- authProvider.storeCredentials(googleCloudApiKey, googlePath.mkString)
      _                      <- authProvider.storeCredentials(keystore, keystorePath.mkString)
      tlsContext             <- authProvider.tlsContextResource(summon[AppConfig].apiKeyStore.keyStoreManagement)
      _                      <- authProvider.initializeSecret(alias = "jwtSigningSecret", summon[AppConfig].apiKeyStore.keyStoreManagement).toResource
      given AuthProvider[IO]  = authProvider
      given JwtProvider[IO]   = DefaultJwtProvider[IO](summon[AppConfig], authProvider)
      given Metrics[IO]      <- Metrics.make[IO].toResource
      given SecureRandom[IO] <- SecureRandom.javaSecuritySecureRandom[IO].toResource
      imageParser             = GoogleCloudClient[IO]
      traceParser             = TraceClient[IO]
      play4sService           =
        Play4sService.make[IO](
          clock = Clock[IO],
          uuidGen = UUIDGen.fromSecureRandom,
          orchestrator = Orchestrator.make[IO](traceParser, imageParser),
          algorithms = BacktrackingAlgorithm.make[IO],
          ConstraintPropagationAlgorithm.make[IO],
        )
      metaRoutes             <- HealthService.make[IO].routes
      authRoutes             <- AuthService.make[IO].routes
      swaggerRoutes           = docs[IO](Play4sApi, ServiceMetaApi, ServiceAuthApi) // todo: fails to render with withHttp2 enabled
      play4sThrottle         <- Middleware.addConcurrentRequestsLimit(play4sService.secureRoutes, 10).toResource
      allRoutes               = authRoutes <+> metaRoutes <+> swaggerRoutes <+> play4sThrottle
      wrappedRoutes           = org.http4s.server.middleware.Logger.httpRoutes(logHeaders = true, logBody = true)(allRoutes)
      _                      <-
        EmberServerBuilder
          .default[IO]
          .withHttp2
          .withPort(summon[AppConfig].http.port)
          .withHost(summon[AppConfig].http.host)
          .withTLS(tlsContext)
          .withHttpApp(wrappedRoutes.orNotFound)
          .build
    } yield ()

}
