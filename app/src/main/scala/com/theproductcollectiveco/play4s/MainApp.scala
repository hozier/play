package com.theproductcollectiveco.play4s

import cats.effect.{IO, ResourceApp, Sync}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import cats.effect.implicits.*
import cats.effect.kernel.Resource
import cats.effect.Clock
import cats.effect.std.UUIDGen
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.api.{Play4sService, HealthService}
import com.theproductcollectiveco.play4s.game.sudoku.common.Parser
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{GoogleCloudClient, TraceClient}
import com.theproductcollectiveco.play4s.config.AppConfig

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] =
    for {
      appConfig        <- AppConfig.load[IO].toResource
      given Logger[IO] <- Slf4jLogger.create[IO].toResource
      given Metrics[IO] = Metrics[IO]
      _                <-
        Parser
          .storeEnvVarContent[IO](
            envValue = appConfig.googleCloud.apiKey.value,
            filePath = appConfig.googleCloud.credentialsFilePath,
          )
          .toResource
      imageParser       = GoogleCloudClient[IO]
      traceParser       = TraceClient[IO]
      play4sService     =
        Play4sService[IO](
          clock = Clock[IO],
          uuidGen = UUIDGen.fromSync[IO](Sync[IO]),
          orchestrator = Orchestrator[IO](traceParser, imageParser),
          algorithms = BacktrackingAlgorithm[IO](),
          ConstraintPropagationAlgorithm[IO](),
        )
      _                <-
        Routes
          .router(play4sService, HealthService[IO])
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

  end run

}
