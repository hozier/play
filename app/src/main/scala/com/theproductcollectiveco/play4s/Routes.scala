package com.theproductcollectiveco.play4s

import cats.implicits.*
import cats.effect.IO
import org.http4s.HttpRoutes
import smithy4s.http4s.SimpleRestJsonBuilder

object Routes {

  def routes(service: Play4sService[IO]): HttpRoutes[IO] =
    SimpleRestJsonBuilder
      .routes(service)
      .make
      .fold(_.raiseError, ok => ok)

}
