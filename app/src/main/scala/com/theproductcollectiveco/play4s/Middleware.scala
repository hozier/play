package com.theproductcollectiveco.play4s

import cats.ApplicativeError
import cats.data.{Kleisli, OptionT}
import cats.effect.{Concurrent, MonadCancelThrow, Async, Resource}
import cats.syntax.all.*

import io.circe.{Encoder, Json}

import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.multipart.*
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.server.middleware.MaxActiveRequests

import org.typelevel.log4cats.Logger

import smithy4s.Blob
import smithy4s.Service
import smithy4s.http4s.SimpleRestJsonBuilder

import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.game.sudoku.{AuthError, DecodeFailureError, GameId}
import io.circe.generic.semiauto.deriveEncoder

object Middleware {

  given gameIdEncoder: Encoder[GameId.T]     = Encoder.encodeString.contramap(_.toString)
  given authErrorEncoder: Encoder[AuthError] = deriveEncoder[AuthError]

  extension [Alg[_[_, _, _, _, _]]: Service, F[_]: Concurrent](
    impl: smithy4s.kinds.FunctorAlgebra[Alg, F]
  ) {

    def routes(using appConfig: AppConfig, logger: Logger[F]): Resource[F, HttpRoutes[F]] = SimpleRestJsonBuilder.routes(impl).resource

    def secureRoutes(using appConfig: AppConfig, logger: Logger[F]): HttpRoutes[F] =
      Kleisli { (req: Request[F]) =>
        req.headers
          .get[Authorization]
          .map(_.value)
          .fold(
            OptionT.liftF(
              Response[F](status = Unauthorized).withEntity(AuthError("Missing API Key")).pure[F]
            )
          ) { apiKey =>
            OptionT.liftF(
              Concurrent[F].ifM((apiKey == s"Bearer ${appConfig.apiKeyStore.app.apiKey.value}").pure)(
                OptionT(
                  SimpleRestJsonBuilder
                    .routes(impl)
                    .resource
                    .use(_.run(req).value)
                ).getOrElseF(forbiddenClientResponse),
                forbiddenClientResponse,
              )
            )
          }
      }

    private def forbiddenClientResponse: F[Response[F]] = Response[F](status = Forbidden).withEntity(AuthError("Forbidden Client")).pure
  }

  def addConcurrentRequestsLimit[F[_]: Async](
    route: HttpRoutes[F],
    limit: Int,
  ): F[HttpRoutes[F]] = MaxActiveRequests.forHttpRoutes[F](limit).map(_(route))

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
