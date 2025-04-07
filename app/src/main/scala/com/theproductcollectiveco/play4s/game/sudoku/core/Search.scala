package com.theproductcollectiveco.play4s.game.sudoku.core

import com.theproductcollectiveco.play4s.game.sudoku.BoardState

trait Search {
  def verifyBoxState(board: BoardState, row: Int, col: Int, target: Int): Boolean
  def verifyColumnState(board: BoardState, col: Int, target: Int): Boolean
  def verifyRowState(board: BoardState, row: Int, target: Int): Boolean
  def verify(boardState: BoardState, row: Int, col: Int, target: Int): Boolean
  def fetchEmptyCells(board: BoardState): LazyList[(Int, Int)]
}

object Search {

  def apply(): Search =
    new Search {

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

      override def fetchEmptyCells(board: BoardState): LazyList[(Int, Int)] = {
        val size = board.value.size
        LazyList
          .from(0 until size * size)
          .filter(idx => board.value(idx / size)(idx % size) == 0)
          .map(idx => (idx / size, idx % size))
      }

    }

}
