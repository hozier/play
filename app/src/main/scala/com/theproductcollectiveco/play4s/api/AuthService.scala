package com.theproductcollectiveco.play4s.api

import cats.effect.{Async, Clock}
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.implicits.*
import com.theproductcollectiveco.play4s.auth.JwtProvider
import com.theproductcollectiveco.play4s.internal.auth.{GenericHandle, ServiceAuthApi, Token}

object AuthService {

  def make[F[_]: Async](using provider: JwtProvider[F]): ServiceAuthApi[F] =
    new ServiceAuthApi[F] {

      override def requestToken(requester: GenericHandle): F[Token] =
        SecureRandom.javaSecuritySecureRandom[F].flatMap { security =>
          given SecureRandom[F] = security
          UUIDGen.fromSecureRandom.randomUUID.map(_.toString).flatMap { uuid =>
            Clock[F].realTimeInstant.flatMap { now =>
              provider
                .generateJwt(
                  handle = requester,
                  expiration = now.getEpochSecond + 300,
                  issuedAt = now.getEpochSecond,
                  roles = "anonymous" :: Nil,
                  tokenId = uuid,
                  additionalClaims = Some(Map("env" -> "production")),
                  oneTimeUse = true,
                  issuer = "app.play4s-service.io",
                )
            }
          }
        }
    }

}
