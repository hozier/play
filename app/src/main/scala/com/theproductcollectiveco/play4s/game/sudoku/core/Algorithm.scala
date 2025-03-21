package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.effect.{Async, MonadCancelThrow}
import org.typelevel.log4cats.Logger
import cats.implicits.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.store.Board
import cats.Parallel
import com.theproductcollectiveco.play4s.game.sudoku.{NoSolutionFoundError, BoardState}

trait Algorithm[F[_]] {
  def solve(board: Board[F], search: Search): F[Boolean]
}

trait BacktrackingAlgorithm[F[_]] extends Algorithm[F] {

  def run(
    board: BoardState,
    search: Search,
  ): F[BoardState]

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
        state: BoardState,
        search: Search,
      ): F[BoardState] =
        search.fetchEmptyCells(state).pure.flatMap { emptyCells =>
          Operations
            .loop(state, search, emptyCells)
            .liftTo[F](NoSolutionFoundError(s"Failed to fill all ${emptyCells.size} empty cells"))
        }
    }

  object Operations {

    def loop(
      state: BoardState,
      search: Search,
      emptyCells: LazyList[(Int, Int)],
    ): Option[BoardState] =

      emptyCells.headOption match {
        case None             => state.some
        case Some((row, col)) =>
          (1 to state.value.size)
            .to(LazyList)
            .flatMap { next =>
              Option.when(search.verify(state, row, col, next)) {
                val updated =
                  state.copy:
                    state.value.updated(row, state.value(row).updated(col, next))
                loop(updated, search, emptyCells.tail)
              }
            }
            .collectFirst { case Some(solution) => solution }
      }

  }

}
