package com.theproductcollectiveco.play4s.auth

import cats.effect.{Async, Clock, MonadCancelThrow}
import cats.effect.implicits.*
import cats.effect.std.{SecureRandom, Supervisor}
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.internal.auth.{AuthEligibilityError, AuthProcessingError, *}
import com.theproductcollectiveco.play4s.internal.auth.Contact.{EmailAddressCase, PhoneNumberCase}
import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp

import java.time.temporal.ChronoUnit

trait OtpProvider[F[_]] {

  def initiateOtp(
    handle: GenericHandle,
    expiryWindow: Long = 5L * 60,
    expiryUnit: ChronoUnit = ChronoUnit.SECONDS,
  ): F[Otp]

  def verifyOtpEligibility(handle: GenericHandle, otp: Otp): F[Boolean]
}

object DefaultOtpProvider {

  def apply[F[_]: Async: MonadCancelThrow: Clock: Supervisor: Logger](appConfig: AppConfig, authProvider: AuthProvider[F]): OtpProvider[F] =
    new OtpProvider[F] {

      override def initiateOtp(
        handle: GenericHandle,
        expiryWindow: Long = 5L * 60,
        expiryUnit: ChronoUnit = ChronoUnit.SECONDS,
      ): F[Otp] =
        handle.validate.flatMap {
          OtpProviderOps.make[F](appConfig, authProvider, _).pure.flatMap {
            _.initializeNewOtpSession(handle, expiryWindow, expiryUnit)
          }
        }

      override def verifyOtpEligibility(handle: GenericHandle, otp: Otp): F[Boolean] =
        handle.validate.flatMap {
          OtpProviderOps.make[F](appConfig, authProvider, _).pure.flatMap {
            _.verifyOtpEligibilityWithSession(handle, otp)
          }
        }
    }

}

trait OtpProviderOps[F[_]] {

  def initializeNewOtpSession(
    handle: GenericHandle,
    expiryWindow: Long,
    expiryUnit: ChronoUnit,
  ): F[Otp]

  def verifyOtpEligibilityWithSession(
    handle: GenericHandle,
    otp: Otp,
  ): F[Boolean]

  def updateOtpSession(
    session: OtpSession,
    initiateAttempts: Option[Int] = None,
    validateAttempts: Option[Int] = None,
    isRedeemed: Option[Boolean] = None,
  ): F[Unit]

  def retrieveOtpSession: F[OtpSession]
}

object OtpProviderOps {

  def make[F[_]: Async: Logger](appConfig: AppConfig, authProvider: AuthProvider[F], supported: EmailAddress | PhoneNumber): OtpProviderOps[F] =
    new OtpProviderOps[F] {

      override def initializeNewOtpSession(
        handle: GenericHandle,
        expiryWindow: Long,
        expiryUnit: ChronoUnit,
      ): F[Otp] =
        retrieveOtpSession
          .flatMap { otpSession =>
            for {
              _ <- runEligibilityChecks(otpSession)
              _ <- updateOtpSession(otpSession, initiateAttempts = otpSession.initiateAttempts.some.map(_ + 1))
            } yield otpSession.otp
          }
          .recoverWith { case _: AuthProcessingError =>
            Logger[F].debug("No existing OTP session found. Initializing a new session...") *>
              generateAndStoreOtp(handle, expiryWindow, expiryUnit)
          }

      private def generateAndStoreOtp(
        handle: GenericHandle,
        expiryWindow: Long,
        expiryUnit: ChronoUnit,
      ): F[Otp] =
        for {
          secureRandom <- SecureRandom.javaSecuritySecureRandom[F]
          randomInt    <- secureRandom.nextIntBounded(1000000)
          generatedOtp  = f"$randomInt%06d"
          now          <- Clock[F].realTimeInstant
          otpSession    =
            OtpSession(
              otp = Otp(generatedOtp),
              expiresAt = Timestamp.fromInstant(now.plus(expiryWindow, expiryUnit)),
              handle = handle,
              initiateAttempts = 1,
            )
          _            <-
            authProvider.initializeSecret(
              alias = supported.toAlias,
              authConfig = appConfig.apiKeyStore.keyStoreManagement,
              providedSecret = otpSession.asJson.noSpaces.some,
            )
        } yield Otp(generatedOtp)

      override def verifyOtpEligibilityWithSession(
        handle: GenericHandle,
        otp: Otp,
      ): F[Boolean] =
        for {
          otpSession <- retrieveOtpSession.handleErrorWith(e => Logger[F].debug(e)("Failed to retrieve OTP session") *> e.raiseError)
          isEligible <- runEligibilityChecks(otpSession, otp.some)
          _          <- updateOtpSession(otpSession, validateAttempts = otpSession.validateAttempts.some.map(_ + 1))
          _          <- if isEligible then markOtpAsRedeemed(otpSession) else Async[F].unit
          _          <- Logger[F].debug(s"OTP validation result: $isEligible for user: $handle")
        } yield isEligible

      private def markOtpAsRedeemed(session: OtpSession): F[Unit] =
        Supervisor[F](await = false).use {
          _.supervise {
            updateOtpSession(session, isRedeemed = true.some) *>
              retrieveOtpSession.flatMap {
                runEligibilityChecks(_).void
                  .handleErrorWith(_.getMessage.pure.flatMap(Logger[F].debug(_)))
              }
          }.flatMap(_.joinWithUnit)
        }

      override def updateOtpSession(
        session: OtpSession,
        initiateAttempts: Option[Int] = None,
        validateAttempts: Option[Int] = None,
        isRedeemed: Option[Boolean] = None,
      ): F[Unit] =
        authProvider
          .initializeSecret(
            supported.toAlias,
            appConfig.apiKeyStore.keyStoreManagement,
            session
              .copy(
                initiateAttempts = initiateAttempts.getOrElse(session.initiateAttempts),
                validateAttempts = validateAttempts.getOrElse(session.validateAttempts),
                isRedeemed = isRedeemed.getOrElse(session.isRedeemed),
              )
              .asJson
              .noSpaces
              .some,
          )
          .void

      override def retrieveOtpSession: F[OtpSession] =
        authProvider
          .retrieveSecret(supported.toAlias, appConfig.apiKeyStore.keyStoreManagement)
          .map(_.value)
          .flatMap(io.circe.parser.decode[OtpSession](_).liftTo[F])

      private def runEligibilityChecks(
        session: OtpSession,
        providedOtp: Option[Otp] = None,
      ): F[Boolean] =
        for {
          now    <- Clock[F].realTimeInstant
          result <-
            Async[F]
              .fromEither {
                providedOtp
                  .fold(
                    session.initiateAttempts.>=(5) -> AuthEligibilityError("Maximum number of initiate attempts exceeded.").asLeft[Boolean] :: Nil
                  )(
                    session.expiresAt.toInstant.isBefore(now) -> AuthEligibilityError("OTP has expired.").asLeft[Boolean] ::
                      session.isRedeemed                      -> AuthEligibilityError("OTP has been redeemed.").asLeft[Boolean] ::
                      session.validateAttempts.>=(5)          -> AuthEligibilityError("Maximum number of validate attempts exceeded.").asLeft[Boolean] ::
                      !session.otp.equals(_)                  -> false.asRight[AuthEligibilityError] :: Nil
                  )
                  .collectFirst { case (true, result) => result }
                  .getOrElse(true.asRight[AuthEligibilityError])
              }
              .onError {
                authProvider.removeSecret(supported.toAlias, appConfig.apiKeyStore.keyStoreManagement) *> _.raiseError
              }
        } yield result

    }

}

extension (handle: GenericHandle)

  def validate[F[_]: Async]: F[EmailAddress | PhoneNumber] =
    Async[F].fromEither {
      handle match {
        case GenericHandle.ContactCase(EmailAddressCase(emailAddress)) => emailAddress.asRight
        case GenericHandle.ContactCase(PhoneNumberCase(phoneNumber))   => phoneNumber.asRight
        case _                                                         => AuthEligibilityError(s"Unsupported handle type: $handle").asLeft
      }
    }

extension (supported: EmailAddress | PhoneNumber) def toAlias: Alias = Alias(supported.asJson.noSpaces)

given Encoder[EmailAddress | PhoneNumber] =
  Encoder.instance {
    case email: EmailAddress => Json.obj("EmailAddress" -> email.asJson)
    case phone: PhoneNumber  => Json.obj("PhoneNumber" -> phone.asJson)
  }
