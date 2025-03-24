package com.theproductcollectiveco.play4s

import cats.effect.{IO, ResourceApp}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import cats.effect.kernel.Resource
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] =

    for {
      given Logger[IO] <- Slf4jLogger.create[IO].toResource
      given Metrics[IO] = Metrics[IO]
      _                <-
        Routes
          .router(Play4sService[IO])
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
