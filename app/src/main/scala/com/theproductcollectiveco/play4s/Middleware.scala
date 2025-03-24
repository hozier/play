package com.theproductcollectiveco.play4s

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.multipart._
import org.http4s.circe.*
import io.circe.{Encoder, Json}
import io.circe.generic.auto.* // Automatically derive encoders
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.game.sudoku.{GameId, Algorithm}
import smithy4s.Blob

object Middleware {

  given gameIdEncoder: Encoder[GameId.T]       = Encoder.encodeString.contramap(_.toString)
  given algorithmEncoder: Encoder[Algorithm.T] = Encoder.instance(algorithm => Json.fromString(algorithm.toString))

  def decodeContent(service: Play4sService[IO])(req: Request[IO])(implicit logger: Logger[IO]): IO[Response[IO]] = {
    req.headers.get[org.http4s.headers.`Content-Type`].map(_.mediaType) match {
      case Some(mediaType) if mediaType.mainType.equals("multipart") && mediaType.subType.equals("form-data") =>
        handleMultipartRequest(req, service)
      case Some(MediaType.application.json) =>
        handleJsonRequest(req, service)
      case _ =>
        BadRequest("Unsupported content type")
    }
  }

  private def handleJsonRequest(req: Request[IO], service: Play4sService[IO])(implicit logger: Logger[IO]): IO[Response[IO]] =
    req.decode[Json] { json =>
      json.hcursor.downField("image").as[String] match {
        case Right(imageBase64) =>
          val bytes = java.util.Base64.getDecoder.decode(imageBase64)
          val blob = Blob(bytes)
          for {
            result <- service.computeSudoku(blob)
            response <- Ok(result.asJson)
          } yield response
        case Left(error) =>
          logger.error(s"Failed to decode JSON: $error") *>
            BadRequest("Invalid JSON format")
      }
    }

  private def handleMultipartRequest(req: Request[IO], service: Play4sService[IO])(implicit logger: Logger[IO]): IO[Response[IO]] =
    req.decode[Multipart[IO]] { m =>
      logger.info(s"Received multipart request with parts: ${m.parts.map(_.name).mkString(", ")}") *> {
        m.parts.find(_.name.contains("image")) match {
          case Some(imagePart) =>
            for {
              bytes <- imagePart.body.compile.toVector.map(_.toArray)
              blob = Blob(bytes)
              result <- service.computeSudoku(blob)
              response <- Ok(result.asJson)
            } yield response
          case None =>
            logger.error("Image part not found") *>
              BadRequest("Image part not found")
        }
      }
    }
}

