package com.theproductcollectiveco.play4s

import cats.effect.Concurrent
import cats.syntax.all.*
import org.http4s.*
import org.http4s.multipart.*
import org.http4s.circe.*
import io.circe.{Encoder, Json}
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.game.sudoku.{GameId, Algorithm}
import smithy4s.Blob

object Middleware {

  given gameIdEncoder: Encoder[GameId.T]       = Encoder.encodeString.contramap(_.toString)
  given algorithmEncoder: Encoder[Algorithm.T] = Encoder.instance(algorithm => Json.fromString(algorithm.toString))

  def decodeContent[F[_]: Concurrent: Logger](service: Play4sService[F])(req: Request[F]): F[Blob] =
    req.headers.get[org.http4s.headers.`Content-Type`].map(_.mediaType) match {
      case Some(mediaType) if mediaType.mainType.equals("multipart") && mediaType.subType.equals("form-data") => decodeMultipartToBlob(req)
      case Some(MediaType.application.json)                                                                   => decodeJsonToBlob(req)
      case _                                                                                                  => Exception("Unsupported content type").raiseError
    }

  private def decodeMultipartToBlob[F[_]: Concurrent: Logger](req: Request[F]): F[Blob] =
    req.attemptAs[Multipart[F]].value.flatMap {
      case Right(multipart) =>
        multipart.parts
          .find(_.name.contains("image"))
          .map(_.body.compile.toVector.map(_.toArray))
          .map(_.map(Blob(_)))
          .get
      case Left(error)      => Exception(s"Failed to decode Multipart: ${error.getMessage}").raiseError
    }

  private def decodeJsonToBlob[F[_]: Concurrent: Logger](req: Request[F]): F[Blob] =
    req.attemptAs[Json].value.flatMap {
      case Right(json) =>
        json.hcursor
          .downField("image")
          .as[String]
          .fold(
            error => Exception(s"Failed to decode image field: ${error.getMessage}").raiseError,
            imageBase64 => {
              val bytes = java.util.Base64.getDecoder.decode(imageBase64)
              Concurrent[F].pure(Blob(bytes))
            },
          )
      case Left(error) => Exception(s"Failed to decode JSON: ${error.getMessage}").raiseError
    }

}
