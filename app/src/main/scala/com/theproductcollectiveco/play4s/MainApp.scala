package com.theproductcollectiveco.play4s

import cats.effect.{IO, ResourceApp}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import cats.effect.kernel.Resource
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import cats.syntax.all.*
import com.theproductcollectiveco.play4s.api.{Play4sService, HealthService}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object MainApp extends ResourceApp.Forever {

  override def run(args: List[String]): Resource[IO, Unit] = {
    val setupSecrets: IO[Unit] = for {
      secretJson <- IO.fromOption(sys.env.get("SECRET_JSON"))(
        new RuntimeException("Error: SECRET_JSON environment variable is not set")
      )
      secretsPath = Paths.get("/secrets/credentials.json")
      
      _ <- IO.println(s"========== secretJson: ${secretJson} ==========")
      _ <- IO(Files.createDirectories(secretsPath.getParent))
      _ <- IO(Files.write(secretsPath, secretJson.getBytes(StandardCharsets.UTF_8)))
      _ <- IO.println("========== CREDENTIALS FILE CREATED ==========")
      _ <- IO.println(new String(Files.readAllBytes(secretsPath), StandardCharsets.UTF_8))
    } yield ()

    val startApp: Resource[IO, Unit] =
      for {
        given Logger[IO] <- Slf4jLogger.create[IO].toResource
        given Metrics[IO] = Metrics[IO]
        _                <-
          Routes
            .router(Play4sService[IO], HealthService[IO])
            .map(_.orNotFound)
            .flatMap: httpApp =>
              Resource.eval:
                BlazeServerBuilder[IO]
                  .bindHttp(8080, "0.0.0.0")
                  .withHttpApp(httpApp)
                  .serve
                  .compile
                  .drain
      } yield ()

    Resource.eval(setupSecrets) *> startApp
  }
}
