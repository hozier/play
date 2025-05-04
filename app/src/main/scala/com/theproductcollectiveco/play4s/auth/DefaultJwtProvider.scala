package com.theproductcollectiveco.play4s.auth

import cats.effect.{Async, Clock}
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.config.{peek, AppConfig}
import com.theproductcollectiveco.play4s.internal.auth.{GenericHandle, Grant, MagicLink, Metadata, Payload, Token}
import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.{AuthScheme, Credentials}
import org.http4s.headers.Authorization
import org.http4s.syntax.all.http4sHeaderSyntax
import org.typelevel.log4cats.Logger
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtOptions}
import pdi.jwt.exceptions.JwtExpirationException

trait JwtProvider[F[_]] {
  def isAuthorized(bearerToken: Authorization): F[Boolean]
  def decodeBearerToken(bearerToken: Authorization): F[Grant]
  def decodeJwt(token: Token): F[Json]
  def prependBearerToApiKey: F[Authorization]
  def isPrimaryAuth: F[Boolean]

  def generateJwt(
    handle: GenericHandle,
    expiration: Long,
    issuedAt: Long,
    roles: List[String],
    tokenId: String,
    additionalClaims: Option[Map[String, String]] = None,
    oneTimeUse: Boolean,
    issuer: String,
  ): F[Token]

}

object DefaultJwtProvider {

  def apply[F[_]: Async: Logger](appConfig: AppConfig, authProvider: AuthProvider[F]): JwtProvider[F] =
    new JwtProvider[F] {

      override def isAuthorized(bearerToken: Authorization): F[Boolean] =
        decodeBearerToken(bearerToken)
          .flatMap { grant =>
            Clock[F].realTimeInstant.map { now =>
              (grant.magicLink.payload.expiration > now.getEpochSecond) &&
              (grant.magicLink.issuer.equals("app.play4s-service.io")) &&
              (grant.magicLink.payload.roles.contains("anonymous"))
              // && grant.magicLink.oneTimeUse // todo: invalidation
            }
          }

      override def decodeJwt(token: Token): F[Json] =
        for {
          jwtSigningSecret <- authProvider.retrieveSecret(alias = "jwtSigningSecret", authConfig = appConfig.apiKeyStore.keyStoreManagement)
          payload          <-
            Async[F]
              .fromTry(
                JwtCirce.decodeJson(
                  token.value,
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

      override def decodeBearerToken(bearerToken: Authorization): F[Grant] =
        decodeJwt(Token(bearerToken.value.stripPrefix("Bearer ").trim))
          .map(_.as[Grant])
          .flatMap(Async[F].fromEither(_))

      override def prependBearerToApiKey: F[Authorization] =
        appConfig.apiKeyStore.app.key.peek.map(Credentials.Token(AuthScheme.Bearer, _)).map(Authorization(_))

      override def isPrimaryAuth: F[Boolean] =
        Async[F]
          .fromOption(appConfig.runtime.withJwt, new RuntimeException("Missing primary authentication mechanism configuration"))

      override def generateJwt(
        handle: GenericHandle,
        expiration: Long,
        issuedAt: Long,
        roles: List[String],
        tokenId: String,
        additionalClaims: Option[Map[String, String]] = None,
        oneTimeUse: Boolean,
        issuer: String,
      ): F[Token] =
        authProvider
          .retrieveSecret(alias = "jwtSigningSecret", authConfig = appConfig.apiKeyStore.keyStoreManagement)
          .flatMap { secretWithAliasjwtSecretKey =>
            Async[F].delay {
              Token(
                JwtCirce.encode(
                  Grant(
                    MagicLink(
                      Payload(handle, expiration, issuedAt, roles, tokenId, additionalClaims.map(_.toMetadata)),
                      oneTimeUse,
                      issuer,
                    )
                  ).asJson.noSpaces,
                  secretWithAliasjwtSecretKey,
                  JwtAlgorithm.HS256,
                )
              )
            }
          }

    }

}

extension (claims: Map[String, String]) def toMetadata: List[Metadata] = claims.map { case (key, value) => Metadata(key, value) }.toList
