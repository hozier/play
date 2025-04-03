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
import com.theproductcollectiveco.play4s.api.Play4sService
import com.theproductcollectiveco.play4s.internal.meta.health.ServiceMetaApi

object Routes {

  import com.theproductcollectiveco.play4s.Middleware.{algorithmEncoder, gameIdEncoder}

  def router(
    play4sService: Play4sService[IO],
    serviceMetaApi: ServiceMetaApi[IO],
  )(using Logger[IO]): Resource[IO, HttpRoutes[IO]] =
    for {
      play4sRoutes      <- SimpleRestJsonBuilder.routes(play4sService).resource
      serviceMetaRoutes <- SimpleRestJsonBuilder.routes(serviceMetaApi).resource
      customRoutes       =
        HttpRoutes.of[IO]:
          case req @ POST -> Root / "game" / "sudoku" / "solve" =>
            Middleware
              .decodeContent(req, "image")
              .flatMap: blob =>
                play4sService
                  .computeSudoku(blob)
                  .flatMap: summary =>
                    Ok(summary.asJson)
      allRoutes         <-
        Resource.pure[IO, HttpRoutes[IO]] {
          customRoutes <+> play4sRoutes <+> serviceMetaRoutes
        }
    } yield allRoutes

}
