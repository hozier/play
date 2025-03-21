package com.theproductcollectiveco.play4s.game.sudoku.core

import com.theproductcollectiveco.play4s.store.Board
import com.theproductcollectiveco.play4s.store.Board.BoardData

trait Search {
  def verifyBoxState(board: BoardData, row: Int, col: Int, target: Int): Boolean
  def verifyColumnState(board: BoardData, col: Int, target: Int): Boolean
  def verifyRowState(board: Board.BoardData, row: Int, target: Int): Boolean
  def verify(boardState: BoardData, row: Int, col: Int, target: Int): Boolean
  def fetchEmptyCells(board: BoardData): LazyList[(Int, Int)]
}

object Search {

  def apply(): Search =
    new Search {

      override def verifyBoxState(
        board: BoardData,
        row: Int,
        col: Int,
        target: Int,
      ): Boolean =
        val boxSize = Math.sqrt(board.size).toInt
        !board
          .slice(row / boxSize * boxSize, row / boxSize * boxSize + boxSize)
          .flatMap(
            _.slice(
              col / boxSize * boxSize,
              col / boxSize * boxSize + boxSize,
            )
          )
          .contains(target)

      override def verifyColumnState(
        board: BoardData,
        col: Int,
        target: Int,
      ): Boolean = !board.exists(_(col) == target)

      override def verifyRowState(
        board: BoardData,
        row: Int,
        target: Int,
      ): Boolean = !board(row).contains(target)

      override def verify(
        boardState: BoardData,
        row: Int,
        col: Int,
        target: Int,
      ): Boolean =
        verifyBoxState(boardState, row, col, target) &&
          verifyColumnState(boardState, col, target) &&
          verifyRowState(boardState, row, target)

      override def fetchEmptyCells(board: BoardData): LazyList[(Int, Int)] =
        for {
          row <- LazyList.range(0, board.size)
          col <- LazyList.range(0, board.size)
          if board(row)(col) == 0
        } yield (row, col)

    }

}
