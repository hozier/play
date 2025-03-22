package com.theproductcollectiveco.play4s

import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import org.http4s.blaze.server.*
import org.http4s.implicits.*

object MainApp extends IOApp.Simple {
  given Logger[IO] = Slf4jLogger.getLogger[IO]
  val httpApp      = Routes.routes(Play4sService[IO]).orNotFound

  def run: IO[Unit] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain

}
