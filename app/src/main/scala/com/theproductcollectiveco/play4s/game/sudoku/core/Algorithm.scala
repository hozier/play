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
              Operations.searchDomain(board, boardState, search, emptyCells, 1 to boardState.value.size)
            }
          }
        }
    }

  object Operations {

    def searchDomain[F[_]: MonadCancelThrow: Async](
      board: Board[F],
      boardState: BoardState,
      search: Search,
      emptyCells: LazyList[(Int, Int)],
      possibleDigits: Seq[Int],
    ): F[Option[BoardState]] =
      loop(boardState, search, emptyCells, possibleDigits)
        .liftTo[F](NoSolutionFoundError("Failed to fill all empty cells"))
        .flatMap: solutionState =>
          board.update(solutionState) `as` solutionState.some

    def loop(
      state: BoardState,
      search: Search,
      emptyCells: LazyList[(Int, Int)],
      possibleDigits: Seq[Int],
    ): Option[BoardState] =
      emptyCells.headOption match {
        case None             => state.some
        case Some((row, col)) =>
          possibleDigits
            .to(LazyList)
            .collectFirstSome { next =>
              Option.when(search.verify(state, row, col, next)) {
                val updated = state.copy(value = state.value.updated(row, state.value(row).updated(col, next)))
                loop(updated, search, emptyCells.tail, possibleDigits)
              }
            }
            .collectFirst { case Some(solution) => solution }
      }

  }

}

object ConstraintPropagationAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Parallel: Metrics](): Algorithm[F] =
    new Algorithm[F] {

      override def solve(
        board: Board[F],
        search: Search,
      ): F[Option[BoardState]] =
        Metrics[F].time("ConstraintPropagationAlgorithm.solve") {
          board.read().flatMap { boardState =>
            search.fetchEmptyCells(boardState).pure.flatMap { emptyCells =>
              val domain =
                emptyCells
                  .map: cell =>
                    cell -> (1 to boardState.value.size).filter(search.verify(boardState, cell._1, cell._2, _))
                  .toMap

              domain
                .foldLeft(Option.empty[BoardState].pure[F]) { (acc, preprocessed) =>
                  acc.flatMap {
                    case Some(solution) => solution.some.pure[F]
                    case None           =>
                      val (emptyCell, possibleDigits) = preprocessed
                      BacktrackingAlgorithm.Operations.searchDomain(board, boardState, search, LazyList(emptyCell), possibleDigits)
                  }
                }
            }
          }
        }
    }

}
