package com.theproductcollectiveco.play4s.game.sudoku.core

import com.theproductcollectiveco.play4s.game.sudoku.BoardState

trait Search {
  def verifyBoxState(board: BoardState, row: Int, col: Int, target: Int): Boolean
  def verifyColumnState(board: BoardState, col: Int, target: Int): Boolean
  def verifyRowState(board: BoardState, row: Int, target: Int): Boolean
  def verify(boardState: BoardState, row: Int, col: Int, target: Int): Boolean
  def fetchEmptyCells(board: BoardState): LazyList[(Int, Int)]
  def isRelated(cell1: (Int, Int), cell2: (Int, Int)): Boolean
  def verifyBoard(boardState: BoardState): Boolean
}

object Search {

  def apply(): Search =
    new Search:

      override def verifyBoxState(
        board: BoardState,
        row: Int,
        col: Int,
        target: Int,
      ): Boolean =
        val boxSize = Math.sqrt(board.value.size).toInt
        !board.value
          .slice(row / boxSize * boxSize, row / boxSize * boxSize + boxSize)
          .flatMap(
            _.slice(
              col / boxSize * boxSize,
              col / boxSize * boxSize + boxSize,
            )
          )
          .contains(target)

      override def verifyColumnState(
        board: BoardState,
        col: Int,
        target: Int,
      ): Boolean = !board.value.exists(_(col) == target)

      override def verifyRowState(
        board: BoardState,
        row: Int,
        target: Int,
      ): Boolean = !board.value(row).contains(target)

      override def verify(
        boardState: BoardState,
        row: Int,
        col: Int,
        target: Int,
      ): Boolean =
        verifyBoxState(boardState, row, col, target) &&
          verifyColumnState(boardState, col, target) &&
          verifyRowState(boardState, row, target)

      override def fetchEmptyCells(board: BoardState): LazyList[(Int, Int)] =
        LazyList.from {
          for
            row <- board.value.indices
            col <- board.value(row).indices
            if board.value(row)(col) == 0
          yield (row, col)
        }

      override def isRelated(cell1: (Int, Int), cell2: (Int, Int)): Boolean = {
        val ((row1, col1), (row2, col2)) = (cell1, cell2)
        row1 == row2 || col1 == col2 || (row1 / 3 == row2 / 3 && col1 / 3 == col2 / 3)
      }

      override def verifyBoard(boardState: BoardState): Boolean = !boardState.value.exists(_.contains(0))

}
