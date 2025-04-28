package com.theproductcollectiveco.play4s

import cats.data.{Kleisli, OptionT}
import cats.effect.Async
import cats.syntax.all.*
import cats.effect.Resource

import io.circe.Encoder
import io.circe.generic.auto.*

import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.syntax.all.http4sHeaderSyntax
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.server.middleware.MaxActiveRequests
import org.typelevel.log4cats.Logger

import smithy4s.Service
import smithy4s.http4s.SimpleRestJsonBuilder

import com.theproductcollectiveco.play4s.game.sudoku.AuthError
import com.theproductcollectiveco.play4s.auth.JwtProvider

object Middleware {

  extension [Alg[_[_, _, _, _, _]]: Service, F[_]: Async: Logger](
    impl: smithy4s.kinds.FunctorAlgebra[Alg, F]
  ) {

    def routes: Resource[F, HttpRoutes[F]] = SimpleRestJsonBuilder.routes(impl).resource

    def secureRoutes(using jwtProvider: JwtProvider[F]): HttpRoutes[F] =
      Kleisli { req =>
        val authHeader = req.headers.get[Authorization].map(_.value)
        authHeader.fold(unauthorizedResponse) { credentials =>
          OptionT(
            jwtProvider.isPrimaryAuth
              .ifM(
                withJwt(req, credentials).value,
                withApiKey(req, credentials).value,
              )
          )
        }
      }

    private def withJwt(req: Request[F], jwt: String)(using
      jwtProvider: JwtProvider[F]
    ): OptionT[F, Response[F]] =
      OptionT.liftF(
        jwtProvider
          .decodeBearerToken(jwt)
          .flatMap { grant =>
            Logger[F].debug(s"Decoded JWT payload: ${grant.magicLink.payload}") *>
              SimpleRestJsonBuilder
                .routes(impl)
                .resource
                .use(_.run(req).value)
                .flatMap(responseOpt => OptionT.fromOption[F](responseOpt).getOrElseF(forbiddenClientResponse))
          }
          .handleErrorWith(error => Logger[F].error(error)("JWT validation failed") *> forbiddenClientResponse)
      )

    private def withApiKey(req: Request[F], apiKey: String)(using
      jwtProvider: JwtProvider[F]
    ): OptionT[F, Response[F]] =
      OptionT.liftF(
        jwtProvider.prependBearerToApiKey.flatMap { expectedApiKey =>
          Async[F].ifM(expectedApiKey.pure.map(_.equals(apiKey)))(
            SimpleRestJsonBuilder
              .routes(impl)
              .resource
              .use(_.run(req).value)
              .flatMap(responseOpt => OptionT.fromOption[F](responseOpt).getOrElseF(forbiddenClientResponse)),
            forbiddenClientResponse,
          )
        }
      )

    private def forbiddenClientResponse: F[Response[F]] = Response[F](status = Forbidden).withEntity(AuthError("Forbidden Client")).pure

    private def unauthorizedResponse: OptionT[F, Response[F]] =
      OptionT.liftF(Response[F](status = Unauthorized).withEntity(AuthError("Missing API Key")).pure)

  }

  def addConcurrentRequestsLimit[F[_]: Async](
    route: HttpRoutes[F],
    limit: Int,
  ): F[HttpRoutes[F]] = MaxActiveRequests.forHttpRoutes[F](limit).map(_(route))

}
