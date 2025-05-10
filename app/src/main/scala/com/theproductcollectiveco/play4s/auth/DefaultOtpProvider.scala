package com.theproductcollectiveco.play4s.auth

import cats.effect.{Async, Clock, MonadCancelThrow}
import cats.effect.implicits.*
import cats.effect.std.{SecureRandom, Supervisor}
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.config.{AppConfig, AuthConfig}
import com.theproductcollectiveco.play4s.game.sudoku.AuthValidationError
import com.theproductcollectiveco.play4s.internal.auth.*
import com.theproductcollectiveco.play4s.internal.auth.Contact.{EmailAddressCase, PhoneNumberCase}
import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp

import java.time.Instant
import java.time.temporal.ChronoUnit

trait OtpProvider[F[_]] {

  def initializeOtp(
    handle: GenericHandle,
    expiryWindow: Long = 5L * 60,
    expiryUnit: ChronoUnit = ChronoUnit.SECONDS,
  ): F[Otp]

  def verifyOtpEligibility(handle: GenericHandle, otp: Otp): F[Boolean]
}

object DefaultOtpProvider {

  def apply[F[_]: Async: MonadCancelThrow: Logger: Supervisor](appConfig: AppConfig, authProvider: AuthProvider[F], clock: Clock[F]): OtpProvider[F] =
    new OtpProvider[F] {

      override def initializeOtp(
        handle: GenericHandle,
        expiryWindow: Long = 5L * 60,
        expiryUnit: ChronoUnit = ChronoUnit.SECONDS,
      ): F[Otp] =
        handle.validate.flatMap { contact =>
          SecureRandom
            .javaSecuritySecureRandom[F]
            .flatMap {
              _.nextIntBounded(1000000).map(randomInt => f"$randomInt%06d").flatMap { generated =>
                clock.realTimeInstant.flatMap { instant =>
                  retrieveOtpSession(using Alias(contact.asJson.noSpaces), appConfig.apiKeyStore.keyStoreManagement)
                    .map(_.otp)
                    .recoverWith {
                      Logger[F].info(_)("No existing OTP session was found in the store. Proceeding with initialization...") *>
                        authProvider.initializeSecret(
                          alias = Alias(contact.asJson.noSpaces),
                          authConfig = appConfig.apiKeyStore.keyStoreManagement,
                          providedSecret =
                            OtpSession(
                              otp = Otp(generated),
                              expiresAt = Timestamp.fromInstant(instant.plus(expiryWindow, expiryUnit)),
                              handle = handle,
                            ).asJson.noSpaces.some,
                        ) as Otp(generated)
                    }
                }
              }
            }
        }

      override def verifyOtpEligibility(handle: GenericHandle, otp: Otp): F[Boolean] =
        for {
          contact          <- handle.validate
          given Alias       = Alias(contact.asJson.noSpaces)
          given AuthConfig  = appConfig.apiKeyStore.keyStoreManagement
          now              <- clock.realTimeInstant
          storedOtpSession <-
            retrieveOtpSession
              .handleErrorWith: e =>
                Logger[F].debug(e)("Failed to retrieve OTP session") *> e.raiseError
          _                <- updateStoredOtpSession(storedOtpSession, validateAttemptState = (storedOtpSession.validateAttempts + 1).some)
          isEligible       <- runEligibilityChecks(storedOtpSession, now, otp)
          _                <-
            isEligible.pure.ifM(
              /**
               * Perform a series of post-processing steps to prevent further use of eligible OTPs after redemption. Gracefully handle any anticipated
               * errors resulting from session state updates caused by OTP redemption. Run these steps in the background using Supervisor.
               */
              Supervisor[F](await = false).use {
                _.supervise {
                  updateStoredOtpSession(storedOtpSession, isRedeemedState = true.some) *>
                    retrieveOtpSession.flatMap {
                      runEligibilityChecks(_, now, otp).void
                        .handleErrorWith {
                          _.getMessage.pure.flatMap {
                            Logger[F].info(_)
                          }
                        }
                    }
                }
                  .flatMap(_.joinWithUnit)
              },
              Async[F].unit,
            )
          _                <- Logger[F].debug(s"OTP validation result: $isEligible for user: $contact")
        } yield isEligible

      private def runEligibilityChecks(
        storedOtpSession: OtpSession,
        now: Instant,
        otp: Otp,
      )(using alias: Alias, authConfig: AuthConfig): F[Boolean] =
        Async[F]
          .fromEither {
            (
              storedOtpSession.expiresAt.toInstant.isBefore(now),
              storedOtpSession.isRedeemed,
              storedOtpSession.validateAttempts >= 5,
              !storedOtpSession.otp.equals(otp),
            ) match
              case (true, _, _, _) => AuthValidationError("OTP has expired.").asLeft[Boolean]
              case (_, true, _, _) => AuthValidationError("OTP has been redeemed.").asLeft[Boolean]
              case (_, _, true, _) => AuthValidationError("Maximum number of attempts exceeded.").asLeft[Boolean]
              case (_, _, _, true) => false.asRight[AuthValidationError]
              case _               => true.asRight[AuthValidationError]
          }
          .onError {
            authProvider.removeSecret(alias, authConfig) *> _.raiseError
          }

      private def updateStoredOtpSession(
        storedOtpSession: OtpSession,
        validateAttemptState: Option[Int] = None,
        isRedeemedState: Option[Boolean] = None,
      )(using alias: Alias, authConfig: AuthConfig): F[Unit] =
        authProvider.initializeSecret(
          alias,
          authConfig,
          storedOtpSession
            .copy(validateAttempts = validateAttemptState.getOrElse(storedOtpSession.validateAttempts))
            .copy(isRedeemed = isRedeemedState.getOrElse(storedOtpSession.isRedeemed))
            .asJson
            .noSpaces
            .some,
        )

      private def retrieveOtpSession(using alias: Alias, authConfig: AuthConfig): F[OtpSession] =
        authProvider
          .retrieveSecret(alias, authConfig)
          .map(io.circe.parser.decode[OtpSession](_))
          .flatMap {
            Async[F].fromEither(_)
          }
    }

}

extension (handle: GenericHandle)

  def validate[F[_]: Async]: F[EmailAddress | PhoneNumber] =
    Async[F].fromEither:
      handle match {
        case GenericHandle.ContactCase(EmailAddressCase(emailAddress)) => emailAddress.asRight[AuthValidationError]
        case GenericHandle.ContactCase(PhoneNumberCase(phoneNumber))   => phoneNumber.asRight[AuthValidationError]
        case GenericHandle.UsernameCase(_)                             =>
          AuthValidationError:
            s"Invalid contact type: Expected EmailAddress or PhoneNumber, but received Username for handle: $handle"
          .asLeft[EmailAddress | PhoneNumber]
      }

given Encoder[EmailAddress | PhoneNumber] =
  Encoder.instance {
    case email: EmailAddress => Json.obj("EmailAddress" -> email.asJson)
    case phone: PhoneNumber  => Json.obj("PhoneNumber" -> phone.asJson)
  }
