package com.theproductcollectiveco.play4s.game.sudoku.common

import cats.effect.{Async, MonadCancelThrow}
import cats.implicits.*
import com.theproductcollectiveco.play4s.game.sudoku.{BoardState, InitialStateSettingError, InvalidInputError}
import fs2.io.file.{Files, Path}
import fs2.text
import org.typelevel.log4cats.Logger

import java.nio.file.Paths

trait Parser[F[_]] {

  private def resolveResourcePath[F[_]: Async](fileName: String): F[Path] =
    Async[F]
      .fromOption(
        getClass.getClassLoader.getResource(fileName).some,
        InitialStateSettingError(s"Resource not found: $fileName"),
      )
      .flatMap: resource =>
        Either
          .catchNonFatal(Paths.get(resource.toURI))
          .leftMap(_ => InitialStateSettingError(s"Invalid URI for resource: $fileName"))
          .liftTo[F]
      .map(Path.fromNioPath)

  def parseLine[F[_]: Async: MonadCancelThrow](str: String): F[BoardState] = {
    val size = Math.sqrt(str.length).toInt
    Option
      .when(size * size == str.length):
        (0 until size).toVector
          .foldLeft(BoardState(Vector.empty[Vector[Int]])) { (acc, i) =>
            val row = str.slice(i * size, (i + 1) * size).map(_.asDigit).toVector
            BoardState(acc.value :+ row)
          }
      .liftTo[F](InvalidInputError(s"Input string length (${str.length}) must be a perfect square."))
  }

  def fetchResourceBytes[F[_]: Async: Files](fileName: String): F[Array[Byte]] =
    resolveResourcePath(fileName).flatMap {
      Files[F].readAll(_).compile.to(Array)
    }

  def readResourceContents[F[_]: Async: Files: Logger](fileName: String, filter: String => Boolean = _ => true): F[List[String]] =
    Logger[F].debug(s"Reading resource file $fileName") *>
      resolveResourcePath(fileName).flatMap { path =>
        Files[F]
          .readAll(path)
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(filter)
          .compile
          .toList
      }

}
