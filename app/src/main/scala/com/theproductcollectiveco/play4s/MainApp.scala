package com.theproductcollectiveco.play4s

import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*

object MainApp extends IOApp.Simple {
  given Logger[IO] = Slf4jLogger.getLogger[IO]

  def run: IO[Unit] = {

    val httpAppResource = Routes.router(Play4sService[IO]).map(_.orNotFound)

    httpAppResource.use { httpApp =>
      BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(httpApp)
        .serve
        .compile
        .drain
    }
  }

}
