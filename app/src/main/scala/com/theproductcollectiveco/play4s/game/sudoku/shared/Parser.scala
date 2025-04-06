package com.theproductcollectiveco.play4s.game.sudoku.shared

import cats.implicits.*
import cats.effect.Async
import java.nio.file.{Paths, Files as Files4j}
import com.theproductcollectiveco.play4s.game.sudoku.BoardState
import cats.effect.Sync
import fs2.io.file.{Files, Path}
import io.circe.{Json, parser}

trait Parser[F[_]] {

  def parseLine(str: String) = {
    val size = Math.sqrt(str.length).toInt
    (0 until size).toVector.foldLeft(BoardState(Vector.empty[Vector[Int]])) { (acc, i) =>
      val row = str.slice(i * size, (i + 1) * size).map(_.asDigit).toVector
      BoardState(acc.value :+ row)
    }
  }

  def fetchBytes[F[_]: Async](fileName: String): F[Array[Byte]] =
    for {
      uri   <- Async[F].delay(getClass.getClassLoader.getResource(fileName).toURI)
      path  <- Async[F].delay(Paths.get(uri))
      bytes <- Async[F].delay(Files4j.readAllBytes(path))
    } yield bytes

  def envVarToFileResource[F[_]: Sync: Files](envVar: String, filePath: String): F[Unit] =
    for {
      // Acquire the environment variable
      envValue <-
        Sync[F].fromOption(
          Option(System.getenv(envVar)),
          new RuntimeException(s"Environment variable $envVar is not set"),
        )

      _ <- Files[F].createDirectories(Path(filePath).parent.getOrElse(Path("."))) // Ensure the directory exists
      _ <-
        fs2.Stream
          .emits(envValue.getBytes)
          .covary[F]
          .through(Files[F].writeAll(Path(filePath)))
          .compile
          .drain

      _       <- Sync[F].delay(println(s"Created file resource $filePath with content $envValue"))
      content <- readFileContents(filePath)
      _       <- Sync[F].delay(println(s"Validating created resource... "))
      _       <- Sync[F].delay(println(s"Formatted file contents: $content"))
    } yield ()

  def readFileContents[F[_]: Sync: Files](filePath: String): F[String] =
    Files[F]
      .readAll(Path(filePath))
      .chunks
      .map(chunk => new String(chunk.toArray, java.nio.charset.StandardCharsets.UTF_8))
      .compile
      .string

}

object JsonFormatter {

  def formatEscapedJson[F[_]: Sync](escapedJson: String): F[String] =
    Sync[F].fromEither(parser.parse(escapedJson)).flatMap { json =>
      def unescapeJson(json: Json): Json =
        json
          .mapObject { obj =>
            obj.mapValues {
              case j if j.isString => j.mapString(_.replace("\\n", "\n"))
              case other           => unescapeJson(other)
            }
          }
          .mapArray(_.map(unescapeJson))

      val updatedJson = unescapeJson(json)

      Sync[F].pure(updatedJson.spaces4)
    }

}
