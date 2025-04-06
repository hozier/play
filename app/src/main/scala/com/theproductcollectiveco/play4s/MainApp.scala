package com.theproductcollectiveco.play4s

import cats.effect.{IO, ResourceApp}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import cats.effect.kernel.Resource
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.api.{Play4sService, HealthService}
import com.theproductcollectiveco.play4s.game.sudoku.shared.Parser

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] =

    for {
      given Logger[IO] <- Slf4jLogger.create[IO].toResource
      given Metrics[IO] = Metrics[IO]
      _                <-
        new Parser[IO] {}
          .envVarToFileResource[IO](envVar = "GOOGLE_CLOUD_API_SAKEY", filePath = "/tmp/secrets/credentials.json")
          .toResource
      _                <-
        Routes
          .router(Play4sService[IO], HealthService[IO])
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
