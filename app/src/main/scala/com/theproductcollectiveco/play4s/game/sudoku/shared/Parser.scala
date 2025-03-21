package com.theproductcollectiveco.play4s.game.sudoku.shared

import cats.implicits.*
import cats.effect.Async
import java.nio.file.{Files, Paths}
import com.theproductcollectiveco.play4s.game.sudoku.BoardState

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
      bytes <- Async[F].delay(Files.readAllBytes(path))
    } yield bytes

}
