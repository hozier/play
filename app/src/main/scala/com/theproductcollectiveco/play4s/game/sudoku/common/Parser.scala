package com.theproductcollectiveco.play4s.game.sudoku.common

import cats.implicits.*
import cats.effect.{Async, MonadCancelThrow}
import java.nio.file.Paths
import com.theproductcollectiveco.play4s.game.sudoku.{BoardState, InvalidInputError, InitialStateSettingError}
import fs2.io.file.{Files, Path}

trait Parser[F[_]]:

  def parseLine[F[_]: Async: MonadCancelThrow](str: String): F[BoardState] = {
    val size = Math.sqrt(str.length).toInt
    Either
      .cond(
        size * size == str.length,
        (0 until size).toVector.foldLeft(BoardState(Vector.empty[Vector[Int]])) { (acc, i) =>
          val row = str.slice(i * size, (i + 1) * size).map(_.asDigit).toVector
          BoardState(acc.value :+ row)
        },
        InvalidInputError(s"Input string length (${str.length}) must be a perfect square."),
      )
      .liftTo[F]
  }

  def fetchBytes[F[_]: Async: Files](fileName: String): F[Array[Byte]] =
    Option(getClass.getClassLoader.getResource(fileName)) match {
      case None           => InitialStateSettingError(s"Resource not found: $fileName").raiseError
      case Some(resource) =>
        Files[F]
          .readAll(Path.fromNioPath(Paths.get(resource.toURI)))
          .compile
          .to(Array)
    }

object Parser:

  def readFileContents[F[_]: Async: Files](filePath: String): F[String] =
    Files[F]
      .readAll(Path(filePath))
      .chunks
      .map(chunk => new String(chunk.toArray, java.nio.charset.StandardCharsets.UTF_8))
      .compile
      .string
