package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.effect.{Async, MonadCancelThrow}
import org.typelevel.log4cats.Logger
import cats.implicits.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.store.Board
import cats.Parallel
import com.theproductcollectiveco.play4s.game.sudoku.{NoSolutionFoundError, BoardState, Strategy}
import com.theproductcollectiveco.play4s.game.sudoku.core.BacktrackingAlgorithm.Operations

case class SolvedState(boardState: BoardState, strategy: Strategy)

trait Algorithm[F[_]] {
  def solve(board: Board[F], search: Search): F[Option[SolvedState]]
}

object BacktrackingAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Parallel: Metrics](): Algorithm[F] =
    new Algorithm[F] {

      override def solve(
        board: Board[F],
        search: Search,
      ): F[Option[SolvedState]] =
        Metrics[F].time("BacktrackingAlgorithm.solve") {
          board.read().flatMap { boardState =>
            search.fetchEmptyCells(boardState).pure.flatMap { emptyCells =>
              Operations
                .searchDomain(board, boardState, search, emptyCells, 1 to boardState.value.size)
                .map(_.map(SolvedState(_, Strategy.BACKTRACKING)))
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
        .liftTo[F](NoSolutionFoundError(s"Failed to fill all ${emptyCells.size} empty cells"))
        .flatMap: solutionState =>
          board.update(solutionState) `as` solutionState.some

    def updateBoardState(domain: Map[(Int, Int), Seq[Int]], currentState: BoardState): BoardState =
      domain.foldLeft(currentState): (state, entry) =>
        val ((row, col), Seq(digit)) = entry
        state.copy(value = state.value.updated(row, state.value(row).updated(col, digit)))

    def loop(
      state: BoardState,
      search: Search,
      emptyCells: LazyList[(Int, Int)],
      possibleDigits: Seq[Int],
    ): Option[BoardState] =

      emptyCells.headOption match {
        case None             => state.some
        case Some((row, col)) =>
          possibleDigits // board state values range
            .to(LazyList)
            .flatMap { next =>
              Option.when(search.verify(state, row, col, next)) {
                loop(updateBoardState(Map((row, col) -> Seq(next)), state), search, emptyCells.tail, possibleDigits)
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
      ): F[Option[SolvedState]] =
        Metrics[F].time("ConstraintPropagationAlgorithm.solve") {
          board.read().flatMap { boardState =>
            search
              .fetchEmptyCells(boardState)
              .map: cell =>
                cell -> (1 to boardState.value.size)
                  .filter(search.verify(boardState, cell._1, cell._2, _))
              .toMap
              .pure
              .flatMap: domainMap =>
                propagateConstraints(boardState, domainMap, search)
                  .filter(search.verifyBoard)
                  .liftTo[F](NoSolutionFoundError(s"Failed to fill all ${domainMap.size} empty cells"))
                  .map:
                    SolvedState(_, Strategy.CONSTRAINT_PROPAGATION).some
          }
        }

      private def propagateConstraints(
        boardState: BoardState,
        domainMap: Map[(Int, Int), Seq[Int]],
        search: Search,
      ): Option[BoardState] = {

        @annotation.tailrec
        def reduceDomain(
          currentDomain: Map[(Int, Int), Seq[Int]],
          acc: Option[Map[(Int, Int), Seq[Int]]] = Some(Map.empty),
        ): Option[Map[(Int, Int), Seq[Int]]] =
          acc match {
            case None         => None
            case Some(domain) =>
              val updatedDomain =
                currentDomain.foldLeft(domain.some) { (acc, entry) =>
                  val (emptyCell, possibleDigits) = entry
                  acc.flatMap: domain =>
                    possibleDigits match

                      /**
                       * State: CellValueDetermined
                       *
                       * When there is exactly one possible digit, the domain for this cell is resolved. This means:
                       *   - The cell is assigned the resolved digit.
                       *   - The domain is updated to reflect this assignment, ensuring that the resolved digit is removed from the domains of all
                       *     related cells (e.g., cells in the same row, column, or block).
                       *   - The domain reduction process continues iteratively until no further changes occur.
                       */
                      case Seq(next) =>
                        val newDomain =
                          domain.map:
                            case (neighbor, neighborDigits) => neighbor -> updateNeighborDomain(neighbor, emptyCell, neighborDigits, next)
                        Option.when(newDomain.values.forall(_.nonEmpty)):
                          newDomain

                      /**
                       * State: DomainReductionStable
                       *
                       * The domain cannot be reduced further at this point. This could mean:
                       *   - The domain is unsolvable (no digits are possible for this cell), or
                       *   - The domain is ambiguous (more than one possible digit remains for some cells).
                       *
                       * A domain is considered stable when no changes occur during the reduction process. Stability indicates that no further
                       * propagation or resolution is possible for the current state of the domain, but it does not necessarily mean the domain is
                       * invalid.
                       *
                       * If the domain remains ambiguous, the algorithm may fall back to backtracking to attempt to resolve the ambiguity.
                       */
                      case _ => acc
                }

              if updatedDomain.equals(acc) then acc
              else reduceDomain(currentDomain, updatedDomain)
          }

        def updateNeighborDomain(
          neighbor: (Int, Int),
          emptyCell: (Int, Int),
          neighborDigits: Seq[Int],
          next: Int,
        ): Seq[Int] =
          neighbor match {
            case `emptyCell`                                => Seq(next)
            case _ if search.isRelated(emptyCell, neighbor) => neighborDigits.filterNot(_ == next)
            case _                                          => neighborDigits
          }

        def fallbackToBacktracking(
          domain: Map[(Int, Int), Seq[Int]],
          currentState: BoardState,
        ): Option[BoardState] =
          domain.collectFirst {
            case (cell, digits) if digits.size > 1 =>
              digits
                .to(LazyList)
                .flatMap { digit =>
                  propagate(
                    currentDomain = domain.updated(cell, Seq(digit)),
                    currentState = currentState,
                  )
                }
                .collectFirst { case solution => solution }
          }.flatten

        @annotation.tailrec
        def propagate(
          currentDomain: Map[(Int, Int), Seq[Int]],
          currentState: BoardState,
          acc: Option[BoardState] = None,
        ): Option[BoardState] =
          reduceDomain(currentDomain) match {
            case None                => acc
            case Some(reducedDomain) =>
              val isDomainStable   = reducedDomain.equals(currentDomain)
              val isDomainResolved = reducedDomain.values.forall(_.size == 1)

              (isDomainStable, isDomainResolved) match {
                case (true, true)  => Some(Operations.updateBoardState(reducedDomain, currentState))
                case (true, false) => fallbackToBacktracking(reducedDomain, currentState)
                case _             => propagate(reducedDomain, currentState, acc)
              }
          }

        propagate(domainMap, boardState)
      }

    }

}
