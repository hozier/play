package com.theproductcollectiveco.play4s.game.sudoku.parser

import cats.effect.{Async, MonadCancelThrow}
import com.theproductcollectiveco.play4s.game.sudoku.common.Parser
import fs2.io.file.Files
import org.typelevel.log4cats.Logger

trait TraceParser[F[_]] extends Parser[F] {
  def parseResource(fileName: String): F[List[String]]
}

object TraceClient {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Files]: TraceParser[F] =
    new TraceParser[F] with Parser[F] {
      override def parseResource(fileName: String): F[List[String]] = super.readResourceContents(fileName, !_.contains("="))
    }

}
