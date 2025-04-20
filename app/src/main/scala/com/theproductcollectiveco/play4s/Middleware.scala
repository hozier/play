package com.theproductcollectiveco.play4s

import cats.effect.{Concurrent, MonadCancelThrow}
import cats.syntax.all.*
import org.http4s.*
import org.http4s.multipart.*
import org.http4s.circe.*
import io.circe.{Encoder, Json}
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.game.sudoku.{GameId, DecodeFailureError}
import smithy4s.Blob
import cats.ApplicativeError

object Middleware {

  given gameIdEncoder: Encoder[GameId.T] = Encoder.encodeString.contramap(_.toString)

  def decodeContent[F[_]: Concurrent: Logger](req: Request[F], field: String): F[Blob] =
    req.headers.get[org.http4s.headers.`Content-Type`].map(_.mediaType) match {
      case Some(mediaType) if mediaType.mainType.equals("multipart") && mediaType.subType.equals("form-data") => decodeMultipartToBlob(req, field)
      case Some(MediaType.application.json)                                                                   => decodeJsonToBlob(req, field)
      case _                                                                                                  => DecodeFailureError("Unsupported content type").raiseError
    }

  private def decodeMultipartToBlob[F[_]: Concurrent: Logger: MonadCancelThrow](req: Request[F], field: String): F[Blob] =
    req
      .attemptAs[Multipart[F]]
      .value
      .flatMap: multipart =>
        multipart.toOption
          .flatMap:
            _.parts
              .find(_.name.contains(field))
              .map(_.body.compile.toVector.map(_.toArray).map(Blob(_)))
          .getOrElse(ApplicativeError[F, Throwable].raiseError(DecodeFailureError("Failed to decode Multipart")))

  private def decodeJsonToBlob[F[_]: Concurrent: MonadCancelThrow: Logger](req: Request[F], field: String): F[Blob] =
    req
      .attemptAs[Json]
      .value
      .flatMap: json =>
        json
          .flatMap:
            _.hcursor
              .downField(field)
              .as[String]
          .map(base64Str => java.util.Base64.getDecoder.decode(base64Str))
          .map(Blob(_).pure)
          .getOrElse(ApplicativeError[F, Throwable].raiseError(new DecodeFailureError("Failed to decode JSON")))

}
