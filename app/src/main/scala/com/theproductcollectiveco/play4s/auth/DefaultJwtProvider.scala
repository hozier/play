package com.theproductcollectiveco.play4s.auth

import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import cats.effect.Async
import io.circe.syntax.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import com.theproductcollectiveco.play4s.config.{AppConfig, peek}
import pdi.jwt.{JwtCirce, JwtOptions, JwtAlgorithm}
import pdi.jwt.exceptions.JwtExpirationException
import com.theproductcollectiveco.play4s.auth.DefaultJwtProvider.*

trait JwtProvider[F[_]] {
  def decodeJwt(token: String): F[Json]
  def decodeBearerToken(token: String): F[Grant]
  def prependBearerToApiKey: F[String]
  def isPrimaryAuth: F[Boolean]

  def generateJwt(
    handle: GenericHandle,
    expiration: Option[Long],
    issuedAt: Option[Long],
    roles: List[String],
    tokenId: Option[String],
    metadata: Option[Map[String, String]],
    oneTimeUse: Boolean,
    issuer: Option[String],
  ): F[String]

}

object DefaultJwtProvider {

  case class Grant(magicLink: MagicLink)

  case class MagicLink(
    payload: Payload,
    oneTimeUse: Boolean,
    issuer: Option[String],
  )

  case class Payload(
    genericHandle: GenericHandle,
    expiration: Option[Long],
    issuedAt: Option[Long],
    roles: List[String],
    tokenId: Option[String],
    metadata: Option[Map[String, String]],
  )

  enum GenericHandle {
    case EmailAddress(value: String)
    case Username(value: String)
  }

  def apply[F[_]: Async: Logger](appConfig: AppConfig, authProvider: AuthProvider[F]): JwtProvider[F] =
    new JwtProvider[F] {
      override def decodeJwt(token: String): F[Json] =
        for {
          jwtSigningSecret <- authProvider.retrieveSecret(alias = "jwtSigningSecret", authConfig = appConfig.apiKeyStore.keyStoreManagement)
          payload          <-
            Async[F]
              .fromTry(
                JwtCirce.decodeJson(
                  token,
                  jwtSigningSecret,
                  Seq(JwtAlgorithm.HS256),
                  JwtOptions(signature = true, expiration = true, notBefore = true, leeway = 1200),
                )
              )
              .adaptError {
                case e: JwtExpirationException => new RuntimeException("JWT has expired", e)
                case e                         => new RuntimeException("Invalid JWT", e)
              }
        } yield payload

      override def decodeBearerToken(token: String): F[Grant] =
        decodeJwt(token.stripPrefix("Bearer ").trim)
          .flatMap(json => Async[F].fromEither(json.as[Grant]))

      override def prependBearerToApiKey: F[String] = appConfig.apiKeyStore.app.key.peek.map("Bearer " + _)

      override def isPrimaryAuth: F[Boolean] =
        Async[F]
          .fromOption(appConfig.runtime.withJwt, new RuntimeException("Missing primary authentication mechanism configuration"))

      override def generateJwt(
        handle: GenericHandle,
        expiration: Option[Long] = Some(System.currentTimeMillis() / 1000 + 3600),
        issuedAt: Option[Long] = Some(System.currentTimeMillis() / 1000),
        roles: List[String] = List("user"),
        tokenId: Option[String] = Some(java.util.UUID.randomUUID().toString),
        metadata: Option[Map[String, String]] = Some(Map("project" -> "play4s", "env" -> "production")),
        oneTimeUse: Boolean = false,
        issuer: Option[String] = Some("app.play4s-service.io"),
      ): F[String] =
        authProvider
          .retrieveSecret(alias = "jwtSigningSecret", authConfig = appConfig.apiKeyStore.keyStoreManagement)
          .flatMap { secretWithAliasjwtSecretKey =>
            Async[F].delay {
              JwtCirce.encode(
                Grant(
                  MagicLink(
                    Payload(handle, expiration, issuedAt, roles, tokenId, metadata),
                    oneTimeUse,
                    issuer,
                  )
                ).asJson.noSpaces,
                secretWithAliasjwtSecretKey,
                JwtAlgorithm.HS256,
              )
            }
          }

    }

}
