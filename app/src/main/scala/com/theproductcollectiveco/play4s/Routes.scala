package com.theproductcollectiveco.play4s

import cats.syntax.all.*
import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.http4s.swagger.docs
import com.theproductcollectiveco.play4s.api.Play4sService
import com.theproductcollectiveco.play4s.internal.meta.health.ServiceMetaApi
import com.theproductcollectiveco.play4s.Play4sApi
import org.http4s.server.middleware.Logger as Http4sLogger

object Routes {

  import com.theproductcollectiveco.play4s.Middleware.gameIdEncoder

  def router(
    play4sService: Play4sService[IO],
    metaService: ServiceMetaApi[IO],
  )(using Logger[IO]): Resource[IO, HttpRoutes[IO]] =
    for {
      play4sRoutes <- SimpleRestJsonBuilder.routes(play4sService).resource
      metaRoutes   <- SimpleRestJsonBuilder.routes(metaService).resource
      swaggerRoutes = docs[IO](Play4sApi, ServiceMetaApi)
      customRoutes  =
        HttpRoutes.of[IO] { case req @ POST -> Root / "public" / "game" / "sudoku" / "solve" =>
          Middleware
            .decodeContent(req, "image")
            .flatMap { blob =>
              play4sService
                .computeSudoku(blob)
                .flatMap(summary => Ok(summary.asJson))
            }
        }
      allRoutes     = customRoutes <+> play4sRoutes <+> metaRoutes <+> swaggerRoutes
    } yield Http4sLogger.httpRoutes(logHeaders = true, logBody = true)(allRoutes)

}
