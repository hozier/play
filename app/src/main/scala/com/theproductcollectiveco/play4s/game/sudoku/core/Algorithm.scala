package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.effect.{Async, MonadCancelThrow}
import cats.Parallel
import cats.implicits.*
import org.typelevel.log4cats.Logger
import scala.collection.immutable.ListMap
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.store.Board
import com.theproductcollectiveco.play4s.game.sudoku.{NoSolutionFoundError, BoardState, Strategy, CellHint, EmptyCell}

case class SolvedState(boardState: BoardState, strategy: Strategy)

trait Algorithm[F[_]] {
  def solve(board: Board[F], search: Search): F[Option[SolvedState]]
}

object BacktrackingAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Parallel](metrics: Metrics[F]): Algorithm[F] =
    new Algorithm[F] {

      override def solve(
        board: Board[F],
        search: Search,
      ): F[Option[SolvedState]] =
        metrics.time("BacktrackingAlgorithm.solve") {
          board.read().flatMap { boardState =>
            search.fetchEmptyCells(boardState).pure.flatMap { emptyCells =>
              Operations
                .searchDomain(board, boardState, search, emptyCells, (1 to boardState.value.size).toList)
                .map:
                  _.map:
                    SolvedState(_, Strategy.BACKTRACKING)
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
      possibleDigits: List[Int],
    ): F[Option[BoardState]] =
      loop(boardState, search, emptyCells, possibleDigits)
        .liftTo[F](NoSolutionFoundError(s"Failed to fill all ${emptyCells.size} empty cells"))
        .flatMap: solutionState =>
          board.update(solutionState) `as` solutionState.some

    def updateBoardState(
      domain: Map[(Int, Int), List[Int]],
      boardState: BoardState,
    ): Option[BoardState] =
      domain.foldLeft(Option(boardState)) {
        case (Some(state), ((row, col), value :: Nil)) => Some(state.copy(value = state.value.updated(row, state.value(row).updated(col, value))))
        case (Some(_), ((_, _), Nil))                  => None // Domain wipeout: cell has no possible values
        case (acc, _)                                  => acc  // Ignore cells with multiple possible values
      }

    def loop(
      state: BoardState,
      search: Search,
      emptyCells: LazyList[(Int, Int)],
      possibleDigits: List[Int],
    ): Option[BoardState] =

      emptyCells.headOption match {
        case None             => state.some
        case Some((row, col)) =>
          possibleDigits
            .to(LazyList)
            .flatMap { next =>
              Option.when(search.verify(state, row, col, next)) {
                updateBoardState(Map((row, col) -> (next :: Nil)), state).flatMap:
                  loop(_, search, emptyCells.tail, possibleDigits)
              }
            }
            .collectFirst { case Some(solution) => solution }
      }

  }

}

object ConstraintPropagationAlgorithm {

  def apply[F[_]: MonadCancelThrow: Async: Parallel: Logger](metrics: Metrics[F]): Algorithm[F] =
    new Algorithm[F] {

      override def solve(
        board: Board[F],
        search: Search,
      ): F[Option[SolvedState]] =
        metrics.time("ConstraintPropagationAlgorithm.solve") {
          board.read().flatMap { boardState =>
            // format: off
            boardState.queryDomain(search, metrics)  // format: on
              .flatMap { (domain, _) =>
                propagateAndSearch(boardState, domain, search).traverse:
                  SolvedState(_, Strategy.CONSTRAINT_PROPAGATION).pure
              }
          }
        }

      private def updateNeighborDomain(
        emotyCell: (Int, Int),
        neighbor: (Int, Int),
        neighborDigits: List[Int],
        value: Int,
        search: Search,
      ): List[Int] =
        (neighbor, emotyCell) match {
          case (n, c) if n == c                 => value :: Nil
          case (n, c) if search.isRelated(c, n) => neighborDigits.filterNot(_ == value)
          case _                                => neighborDigits
        }

      private def propagateAndSearch(
        boardState: BoardState,
        domain: Map[(Int, Int), List[Int]],
        search: Search,
      ): Option[BoardState] = {

        @annotation.tailrec
        def propagate(
          domainState: Map[(Int, Int), List[Int]]
        ): Option[Map[(Int, Int), List[Int]]] = {
          val propagatedDomain =
            domainState.foldLeft(Option(domainState)) {

              /**
               * State: CellValueDetermined
               *
               * If a cell's domain contains exactly one digit, assign that digit to the cell. Update the domains of all related cells (same row,
               * column, or block) by removing this digit. Continue propagation until no further assignments can be made. If any cell's domain becomes
               * empty, the puzzle is unsolvable from this state.
               */
              case (Some(currentDomain), (cell, determinedValue :: Nil)) =>
                currentDomain
                  .map { case (otherCell, possibleValues) =>
                    otherCell -> updateNeighborDomain(cell, otherCell, possibleValues, determinedValue, search)
                  }
                  .some
                  .flatMap: updatedDomain =>
                    Option.when(updatedDomain.values.forall(_.nonEmpty))(updatedDomain)

              /**
               * State: DomainStableOrAmbiguous
               *
               * If no domains can be further reduced (no cell has a single possible digit), propagation is complete. The domain may be ambiguous
               * (multiple possibilities remain) or unsolvable (an empty domain exists). Further resolution may require backtracking.
               */
              case (acc, _) => acc
            }
          if propagatedDomain == Some(domainState) then propagatedDomain else propagate(propagatedDomain.getOrElse(domainState))
        }

        for {
          reducedDomain <- propagate(domain)
          reducedBoard  <- BacktrackingAlgorithm.Operations.updateBoardState(reducedDomain, boardState)
          emptyCells     = search.fetchEmptyCells(reducedBoard)
          solved        <-
            BacktrackingAlgorithm.Operations.loop(
              reducedBoard,
              search,
              emptyCells,
              (1 to boardState.value.size).toList,
            )
        } yield solved
      }

    }

}

extension [F[_]: MonadCancelThrow: Logger: Async](boardState: BoardState)

  def queryDomain(search: Search, metrics: Metrics[F], hintCount: Option[Int] = None): F[(Map[(Int, Int), List[Int]], Int)] =
    metrics.time("BoardState.queryDomain") {
      for
        emptyCells      <- search.fetchEmptyCells(boardState).pure
        limitedCells     = hintCount.fold(emptyCells)(emptyCells.take)
        domainCollection =
          limitedCells.map { case (row, col) => (row, col) -> (1 to boardState.value.size).toList.filter(search.verify(boardState, row, col, _)) }

        /** Minimum Remaining Values (MRV) */
        sortedDomain = ListMap(domainCollection.toMap.toSeq.sortBy { case (_, candidates) => candidates.size }*)
      yield (sortedDomain, emptyCells.size)
    }

extension (domain: Map[(Int, Int), List[Int]])

  def asHints: List[CellHint] = domain.toList.map { case ((row, col), possibleDigits) => CellHint(EmptyCell(row, col), possibleDigits) }
