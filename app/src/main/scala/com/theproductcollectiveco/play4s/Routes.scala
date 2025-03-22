package com.theproductcollectiveco.play4s

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import smithy4s.http4s.SimpleRestJsonBuilder
import cats.effect.IO

object Routes {
  def routes(service: Play4sService[IO]): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._

    SimpleRestJsonBuilder.routes(service).make.fold(
      err => HttpRoutes.of[IO] { case _ => InternalServerError(err.toString) },
      routes => routes
    )
  }
}