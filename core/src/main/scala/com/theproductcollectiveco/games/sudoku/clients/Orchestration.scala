package com.theproductcollectiveco.games.sudoku.clients

import cats.effect.{Async, MonadCancelThrow, Resource}
import cats.effect.kernel.Ref
import org.typelevel.log4cats.Logger
import cats.effect.std.Console
import cats.implicits.*
import scala.io.Source
import com.theproductcollectiveco.Metrics
import com.theproductcollectiveco.store.Board
import cats.Parallel

trait Orchestration[F[_]] {
  def parseResource(fileName: String): F[List[String]]
  def parseLine(str: String): Board.BoardData
  def createBoard(state: Board.BoardData): F[Board[F]]

  def solve(
    board: Board[F],
    search: Search,
    algorithms: Algorithm[F]*
  ): F[Unit]

}

object Orchestration {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console: Parallel: Metrics](): Orchestration[F] =
    new Orchestration[F] {
      override def parseResource(fileName: String): F[List[String]] =
        Logger[F].debug(s"Reading file $fileName") *> {
          val resource =
            Resource.fromAutoCloseable(
              Async[F].delay(Source.fromResource(fileName))
            )
          resource.use { source =>
            Async[F].delay {
              source
                .getLines()
                .toList
                .filterNot(_.contains("="))
            }
          }
        }
      override def parseLine(str: String): Board.BoardData          = {
        val size = Math.sqrt(str.length).toInt
        val rows =
          for (i <- 0 until size)
            yield str.slice(i * size, (i + 1) * size).map(_.asDigit).toVector
        rows.toVector
      }
      override def createBoard(state: Board.BoardData): F[Board[F]] =
        for {
          ref   <- Ref.of[F, Option[Board.BoardData]](None)
          board <- Logger[F].debug("Creating board") *> Board(state, ref)
        } yield board

      override def solve(
        board: Board[F],
        search: Search,
        algorithms: Algorithm[F]*
      ): F[Unit] =
        for {
          _ <- Logger[F].debug("Solving board")
          _ <- algorithms.parTraverse(_.solve(board, search))
          _ <- Logger[F].debug("Board solved")
        } yield ()
    }

}
