package com.theproductcollectiveco.play4s

import cats.syntax.all.*
import cats.effect.{IO, Resource}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.multipart.*
import org.http4s.circe.*
import org.http4s.HttpRoutes
import com.theproductcollectiveco.play4s.game.sudoku.{GameId, Algorithm, SudokuComputationSummary}
import io.circe.{Encoder, Json}
import io.circe.generic.auto.* // Automatically derive encoders
import io.circe.syntax.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.Blob

given gameIdEncoder: Encoder[GameId.T]       = Encoder.encodeString.contramap(_.toString)
given algorithmEncoder: Encoder[Algorithm.T] = Encoder.instance(algorithm => Json.fromString(algorithm.toString))
given Logger[IO]                             = Slf4jLogger.getLogger[IO]

object Routes {

  def router(service: Play4sService[IO]): Resource[IO, HttpRoutes[IO]] = {
    val jsonRoutes = SimpleRestJsonBuilder.routes(service).resource

    val customRoutes =
      HttpRoutes.of[IO] { case req @ POST -> Root / "game" / "sudoku" / "solve" =>
        req.headers.get[org.http4s.headers.`Content-Type`].map(_.mediaType) match {
          case Some(mediaType) if mediaType.mainType.equals("multipart") & mediaType.subType.equals("form-data") =>
            handleMultipartRequest(req, service)
          case Some(MediaType.application.json)                                                                  => handleJsonRequest(req, service)
          case _                                                                                                 => BadRequest("Unsupported content type")
        }
      }

    jsonRoutes.flatMap { jsonR =>
      Resource.pure[IO, HttpRoutes[IO]] {
        customRoutes <+> jsonR
      }
    }
  }

  private def handleJsonRequest(req: Request[IO], service: Play4sService[IO]): IO[Response[IO]] =
    req.decode[Json]:
      _.hcursor.downField("image").as[String] match {
        case Right(imageBase64) =>
          val bytes = java.util.Base64.getDecoder.decode(imageBase64)
          val blob  = Blob(bytes)
          for {
            result   <- service.computeSudoku(blob)
            response <- Ok(result.asJson)
          } yield response
        case Left(error)        =>
          Logger[IO].error(s"Failed to decode JSON: $error") *>
            BadRequest("Invalid JSON format")
      }

  private def handleMultipartRequest(req: Request[IO], service: Play4sService[IO]): IO[Response[IO]] =
    req.decode[Multipart[IO]]: m =>
      Logger[IO].info(s"Received multipart request with parts: ${m.parts.map(_.name).mkString(", ")}") *> {
        m.parts.find(_.name.contains("image")) match {
          case Some(imagePart) =>
            for {
              bytes    <- imagePart.body.compile.toVector.map(_.toArray)
              blob      = Blob(bytes)
              result   <- service.computeSudoku(blob)
              response <- Ok(result.asJson)
            } yield response
          case None            =>
            Logger[IO].error("Image part not found") *>
              BadRequest("Image part not found")
        }
      }

}
