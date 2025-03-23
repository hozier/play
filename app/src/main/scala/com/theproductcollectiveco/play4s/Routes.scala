package com.theproductcollectiveco.play4s

import cats.effect.{IO, Resource}
import smithy4s.http4s.SimpleRestJsonBuilder
import org.http4s.HttpRoutes

object Routes {
  def router(service: Play4sService[IO]): Resource[IO, HttpRoutes[IO]] = SimpleRestJsonBuilder.routes(service).resource
}
