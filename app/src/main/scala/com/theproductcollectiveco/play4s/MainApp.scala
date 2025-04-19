package com.theproductcollectiveco.play4s

import cats.effect.{IO, ResourceApp, Sync, Async}
import cats.effect.IO.asyncForIO
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import cats.effect.implicits.*
import cats.effect.kernel.Resource
import cats.effect.Clock
import cats.effect.std.UUIDGen
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.api.{Play4sService, HealthService}
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{GoogleCloudClient, TraceClient}
import com.theproductcollectiveco.play4s.config.{AppConfig, GoogleCloudConfig, storeCredentials}
import fs2.io.file.Files

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] =
    for {
      appConfig                                                   <- AppConfig.load[IO]
      AppConfig(GoogleCloudConfig(apiKey, credentialsFilePath), _) = appConfig
      _                                                           <- apiKey.storeCredentials(credentialsFilePath)
      given Logger[IO]                                            <- Slf4jLogger.create[IO].toResource
      given Async[IO]                                              = asyncForIO
      given Metrics[IO]                                            = Metrics[IO]
      imageParser                                                  = GoogleCloudClient[IO]
      traceParser                                                  = TraceClient[IO]
      play4sService                                                =
        Play4sService[IO](
          clock = Clock[IO],
          uuidGen = UUIDGen.fromSync[IO](Sync[IO]),
          orchestrator = Orchestrator[IO](traceParser, imageParser),
          algorithms = BacktrackingAlgorithm[IO](),
          ConstraintPropagationAlgorithm[IO](),
        )
      _                                                           <-
        Routes
          .router(play4sService, HealthService[IO](appConfig))
          .map(_.orNotFound)
          .flatMap: httpApp =>
            Resource.eval:
              BlazeServerBuilder[IO]
                .bindHttp(8080, "0.0.0.0")
                .withHttpApp(httpApp)
                .serve
                .compile
                .drain
    } yield ()

}
