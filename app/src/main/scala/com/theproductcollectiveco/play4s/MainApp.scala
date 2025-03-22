package com.theproductcollectiveco.play4s

import cats.effect.IO
import org.http4s.blaze.server._
import org.http4s.implicits._

object MainApp {

  val httpApp = Routes.routes(Play4sService[IO]).orNotFound

  def run: IO[Unit] = {
    BlazeServerBuilder[IO] // swap for emberClient
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
  }

}
