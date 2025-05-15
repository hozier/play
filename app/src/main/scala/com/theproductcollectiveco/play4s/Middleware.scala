package com.theproductcollectiveco.play4s

import cats.data.{Kleisli, OptionT}
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.auth.JwtProvider
import com.theproductcollectiveco.play4s.internal.auth.AuthError
import io.circe.Encoder
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.server.middleware.MaxActiveRequests
import org.typelevel.log4cats.Logger
import smithy4s.Service
import smithy4s.http4s.SimpleRestJsonBuilder

object Middleware {

  extension [Alg[_[_, _, _, _, _]]: Service, F[_]: Async: Logger](
    impl: smithy4s.kinds.FunctorAlgebra[Alg, F]
  ) {

    def routes: Resource[F, HttpRoutes[F]] = SimpleRestJsonBuilder.routes(impl).resource

    def secureRoutes(using jwtProvider: JwtProvider[F]): HttpRoutes[F] =
      Kleisli { req =>
        req.headers
          .get[Authorization]
          .fold(unauthorizedResponse) { credentials =>
            OptionT(
              jwtProvider.isPrimaryAuth
                .ifM(
                  withJwt(req, credentials).value,
                  withApiKey(req, credentials).value,
                )
            )
          }
      }

    private def withJwt(req: Request[F], bearerToken: Authorization)(using
      jwtProvider: JwtProvider[F]
    ): OptionT[F, Response[F]] =
      OptionT.liftF:
        jwtProvider
          .isAuthorized(bearerToken)
          .ifM(
            Logger[F].debug("Decoded JWT is Authorized") *>
              routes
                .use(_.run(req).value)
                .flatMap(OptionT.fromOption[F](_).getOrElseF(forbiddenClientResponse)),
            forbiddenClientResponse,
          )
          .handleErrorWith(Logger[F].error(_)("JWT validation failed") *> forbiddenClientResponse)

    private def withApiKey(req: Request[F], apiKey: Authorization)(using
      jwtProvider: JwtProvider[F]
    ): OptionT[F, Response[F]] =
      OptionT.liftF:
        Async[F].ifM(jwtProvider.prependBearerToApiKey.map(_.equals(apiKey)))(
          routes
            .use(_.run(req).value)
            .flatMap(OptionT.fromOption[F](_).getOrElseF(forbiddenClientResponse)),
          forbiddenClientResponse,
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
