package com.theproductcollectiveco.play4s

import cats.effect.{Async, Clock, IO, Resource, ResourceApp, Sync}
import cats.effect.IO.asyncForIO
import cats.effect.implicits.*
import cats.effect.std.UUIDGen
import fs2.io.file.Files
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.theproductcollectiveco.play4s.api.{HealthService, Play4sService}
import com.theproductcollectiveco.play4s.config.{AppConfig, GoogleCloudConfig, storeCredentials}
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{GoogleCloudClient, TraceClient}

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] =
    for {
      appConfig                                                   <- AppConfig.load[IO]
      AppConfig(GoogleCloudConfig(apiKey, credentialsFilePath), _) = appConfig
      _                                                           <- apiKey.storeCredentials(credentialsFilePath)
      given Logger[IO]                                            <- Slf4jLogger.create[IO].toResource
      given Async[IO]                                              = asyncForIO
      given Metrics[IO]                                           <- Metrics.make[IO].toResource
      imageParser                                                  = GoogleCloudClient[IO]
      traceParser                                                  = TraceClient[IO]
      play4sService                                                =
        Play4sService.make[IO](
          clock = Clock[IO],
          uuidGen = UUIDGen.fromSync[IO](Sync[IO]),
          orchestrator = Orchestrator.make[IO](traceParser, imageParser),
          algorithms = BacktrackingAlgorithm.make[IO],
          ConstraintPropagationAlgorithm.make[IO],
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
