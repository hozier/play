package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.effect.{Async, MonadCancelThrow}
import org.typelevel.log4cats.Logger
import cats.implicits.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.store.Board
import cats.Parallel

trait Algorithm[F[_]] {
  def solve(board: Board[F], search: Search): F[Boolean]
}

trait BacktrackingAlgorithm[F[_]] extends Algorithm[F] {

  def run(
    board: Board.BoardData,
    search: Search,
  ): F[Board.BoardData]

}

object BacktrackingAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Parallel: Metrics](): Algorithm[F] =
    new BacktrackingAlgorithm[F] {

      override def solve(
        board: Board[F],
        search: Search,
      ): F[Boolean] =
        Metrics[F].time("BacktrackingAlgorithm.solve") {
          board.read().flatMap { boardState =>
            run(boardState, search)
              .flatMap:
                board.update(_) `as` true
          }
        }

      override def run(
        boardData: Board.BoardData,
        search: Search,
      ): F[Board.BoardData] =
        Operations
          .loop(boardData, search, search.fetchEmptyCells(boardData))
          .liftTo[F](new Exception("No solution found"))

    }

  object Operations {

    def loop(
      data: Board.BoardData,
      search: Search,
      emptyCells: LazyList[(Int, Int)],
    ): Option[Board.BoardData] =

      emptyCells.headOption match {
        case None             => Some(data)
        case Some((row, col)) =>
          (1 to data.size)
            .to(LazyList)
            .flatMap { next =>
              Option.when(search.verify(data, row, col, next)) {
                val updatedBoardData =
                  data.updated(
                    row,
                    data(row)
                      .updated(col, next),
                  )
                loop(updatedBoardData, search, emptyCells.tail)
              }
            }
            .collectFirst { case Some(solution) => solution }
      }

  }

}
