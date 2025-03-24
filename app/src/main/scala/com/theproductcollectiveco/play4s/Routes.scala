package com.theproductcollectiveco.play4s

import cats.syntax.all.*
import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.auto.* // Automatically derive encoders
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import smithy4s.http4s.SimpleRestJsonBuilder

object Routes {

  import com.theproductcollectiveco.play4s.Middleware.{algorithmEncoder, gameIdEncoder}

  def router(service: Play4sService[IO])(using Logger[IO]): Resource[IO, HttpRoutes[IO]] =
    for {
      jsonRoutes  <- SimpleRestJsonBuilder.routes(service).resource
      customRoutes =
        HttpRoutes.of[IO]:
          case req @ POST -> Root / "game" / "sudoku" / "solve" =>
            Middleware
              .decodeContent(service)(req)
              .flatMap: blob =>
                service
                  .computeSudoku(blob)
                  .flatMap: summary =>
                    Ok(summary.asJson)
      allRoutes   <-
        Resource.pure[IO, HttpRoutes[IO]] {
          customRoutes <+> jsonRoutes
        }
    } yield allRoutes

}
