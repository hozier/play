package com.theproductcollectiveco.play4s.api

import cats.effect.{Async, Clock}
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.implicits.*
import com.theproductcollectiveco.play4s.auth.{JwtProvider, OtpProvider}
import com.theproductcollectiveco.play4s.game.sudoku.InvalidInputError
import com.theproductcollectiveco.play4s.internal.auth.{GenericHandle, Otp, ServiceAuthApi, Token}
import org.typelevel.log4cats.Logger

object AuthService {

  def make[F[_]: Async: Logger](using jwtProvider: JwtProvider[F], otpProvider: OtpProvider[F]): ServiceAuthApi[F] =
    new ServiceAuthApi[F] {

      override def requestToken(requester: GenericHandle): F[Token] =
        SecureRandom.javaSecuritySecureRandom[F].flatMap { security =>
          given SecureRandom[F] = security
          UUIDGen.fromSecureRandom.randomUUID.map(_.toString).flatMap { uuid =>
            Clock[F].realTimeInstant.flatMap { now =>
              jwtProvider
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

      override def initiateOtp(requester: GenericHandle): F[Unit] =
        for {
          otp <- otpProvider.initiateOtp(requester)
          // todo: send the otp via sms or email client integration
          _   <- Logger[F].info(s"Initialized otp: $otp")
        } yield ()

      override def redeemOtp(requester: GenericHandle, otp: Otp): F[Token] =
        otpProvider
          .verifyOtpEligibility(requester, otp)
          .ifM(requestToken(requester), InvalidInputError("Invalid OTP supplied").raiseError)

    }

}
