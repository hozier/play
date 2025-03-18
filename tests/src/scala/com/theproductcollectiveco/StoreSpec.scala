package com.theproductcollectiveco.test

import cats.effect.{IO, Ref}
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.store.Board
import com.theproductcollectiveco.Metrics

object StoreSpec extends SimpleIOSuite with Checkers {

  given Logger[IO]  = Slf4jLogger.getLogger[IO]
  given Metrics[IO] = Metrics[IO]

  test("Board should read initial state correctly") {
    val initialBoardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    for {
      ref       <- Ref.of[IO, Option[Board.BoardData]](Some(initialBoardData))
      gameBoard <- Board[IO](initialBoardData, ref)
      state     <- gameBoard.read()
    } yield expect(state == initialBoardData)
  }

  test("Board should update state correctly") {
    val initialBoardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    val updatedBoardData =
      Vector(
        Vector(5, 3, 4, 6, 7, 8, 9, 1, 2),
        Vector(6, 7, 2, 1, 9, 5, 3, 4, 8),
        Vector(1, 9, 8, 3, 4, 2, 5, 6, 7),
        Vector(8, 5, 9, 7, 6, 1, 4, 2, 3),
        Vector(4, 2, 6, 8, 5, 3, 7, 9, 1),
        Vector(7, 1, 3, 9, 2, 4, 8, 5, 6),
        Vector(9, 6, 1, 5, 3, 7, 2, 8, 4),
        Vector(2, 8, 7, 4, 1, 9, 6, 3, 5),
        Vector(3, 4, 5, 2, 8, 6, 1, 7, 9),
      )

    for {
      ref       <- Ref.of[IO, Option[Board.BoardData]](Some(initialBoardData))
      gameBoard <- Board[IO](initialBoardData, ref)
      _         <- gameBoard.update(updatedBoardData)
      state     <- gameBoard.read()
    } yield expect(state == updatedBoardData)
  }

  test("Board should delete state correctly") {
    val initialBoardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    for {
      ref         <- Ref.of[IO, Option[Board.BoardData]](Some(initialBoardData))
      gameBoard   <- Board[IO](initialBoardData, ref)
      _           <- gameBoard.delete()
      isStateless <- gameBoard.read().attempt.map(_.isLeft)
    } yield expect(isStateless)
  }

}
