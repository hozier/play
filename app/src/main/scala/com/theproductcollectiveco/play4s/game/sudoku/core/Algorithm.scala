package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.effect.{Async, MonadCancelThrow}
import org.typelevel.log4cats.Logger
import cats.implicits.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.store.Board
import cats.Parallel
import com.theproductcollectiveco.play4s.game.sudoku.{NoSolutionFoundError, BoardState}

trait Algorithm[F[_]] {
  def solve(board: Board[F], search: Search): F[Option[BoardState]]
}

object BacktrackingAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Parallel: Metrics](): Algorithm[F] =
    new Algorithm[F] {

      override def solve(
        board: Board[F],
        search: Search,
      ): F[Option[BoardState]] =
        Metrics[F].time("BacktrackingAlgorithm.solve") {
          board.read().flatMap { boardState =>
            search.fetchEmptyCells(boardState).pure.flatMap { emptyCells =>
              Operations
                .loop(boardState, search, emptyCells)
                /**
                 * we can opt to return the option type to the caller as this does save on additional type transformations. the liftTo might be
                 * considered an aggressive evaluation--reconsider passing through unfiltered state to caller (todo)
                 */
                .liftTo[F](NoSolutionFoundError(s"Failed to fill all ${emptyCells.size} empty cells"))
                .flatMap: solutionState =>
                  board.update(solutionState) `as` solutionState.some
            }
          }
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
          (1 to state.value.size) // BoardState values range
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

object ConstaintPropogationAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Parallel: Metrics](): Algorithm[F] =
    new Algorithm[F] {
      override def solve(
        board: Board[F],
        search: Search,
      ): F[Option[BoardState]] =
        Metrics[F].time("ConstaintPropogationAlgorithm.solve") {
          ???
        }
    }

}
