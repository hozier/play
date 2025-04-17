package com.theproductcollectiveco.play4s.game.sudoku.common

import cats.implicits.*
import cats.effect.Async
import java.nio.file.Paths
import com.theproductcollectiveco.play4s.game.sudoku.BoardState
import fs2.io.file.{Files, Path}

trait Parser[F[_]]:

  def parseLine(str: String): BoardState = {
    val size = Math.sqrt(str.length).toInt
    (0 until size).toVector.foldLeft(BoardState(Vector.empty[Vector[Int]])) { (acc, i) =>
      val row = str.slice(i * size, (i + 1) * size).map(_.asDigit).toVector
      BoardState(acc.value :+ row)
    }
  }

  def fetchBytes[F[_]: Async: Files](fileName: String): F[Array[Byte]] =
    Option(getClass.getClassLoader.getResource(fileName)) match {
      case None           => Async[F].raiseError(new RuntimeException(s"Resource not found: $fileName"))
      case Some(resource) =>
        Files[F]
          .readAll(Path.fromNioPath(Paths.get(resource.toURI)))
          .compile
          .to(Array)
    }

object Parser:

  def storeEnvVarContent[F[_]: Async: Files](envValue: String, filePath: String): F[Unit] =
    Files[F]
      .createDirectories(Path(filePath).parent.getOrElse(Path(".")))
      .flatMap { _ =>
        fs2.Stream
          .emits(envValue.getBytes)
          .through(Files[F].writeAll(Path(filePath)))
          .compile
          .drain
      }

  def readFileContents[F[_]: Async: Files](filePath: String): F[String] =
    Files[F]
      .readAll(Path(filePath))
      .chunks
      .map(chunk => new String(chunk.toArray, java.nio.charset.StandardCharsets.UTF_8))
      .compile
      .string
