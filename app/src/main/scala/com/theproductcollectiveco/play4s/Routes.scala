package com.theproductcollectiveco.play4s

import cats.syntax.all.*
import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.dsl.io.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import smithy4s.http4s.SimpleRestJsonBuilder

given Logger[IO] = Slf4jLogger.getLogger[IO]

object Routes {

  def router(service: Play4sService[IO]): Resource[IO, HttpRoutes[IO]] =
    for {
      jsonRoutes  <- SimpleRestJsonBuilder.routes(service).resource
      customRoutes = HttpRoutes.of[IO] { case req @ POST -> Root / "game" / "sudoku" / "solve" => Middleware.decodeContent(service)(req) }
      allRoutes   <-
        Resource.pure[IO, HttpRoutes[IO]] {
          customRoutes <+> jsonRoutes
        }
    } yield allRoutes

}
