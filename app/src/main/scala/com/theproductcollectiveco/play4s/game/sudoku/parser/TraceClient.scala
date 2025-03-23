package com.theproductcollectiveco.play4s.game.sudoku.parser

import com.theproductcollectiveco.play4s.game.sudoku.shared.Parser
import cats.effect.{Async, MonadCancelThrow, Resource}
import org.typelevel.log4cats.Logger
import cats.implicits.*
import scala.io.Source

trait TraceParser[F[_]] extends Parser[F] {
  def parseResource(fileName: String): F[List[String]]
}

object TraceClient {

  def apply[F[_]: MonadCancelThrow: Async: Logger]: TraceParser[F] =
    new TraceParser[F] with Parser[F] {
      override def parseResource(fileName: String): F[List[String]] =
        Logger[F].debug(s"Reading file $fileName") *> {
        Resource.fromAutoCloseable:
          Async[F].delay(Source.fromResource(fileName))
        .use { source =>
          Async[F].delay:
            source
              .getLines()
              .toList
              .filterNot(_.contains("="))
          }
        }

    }

}
