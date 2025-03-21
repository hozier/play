package com.theproductcollectiveco.play4s

import cats.effect.{IO, Ref}
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.store.Board
import com.theproductcollectiveco.play4s.shared.Models

object StoreSpec extends SimpleIOSuite with Checkers {

  given Logger[IO]  = Slf4jLogger.getLogger[IO]
  given Metrics[IO] = Metrics[IO]

  test("Board should read initial state correctly") {
    val initialBoardData = Models.expectedBoardData

    for {
      ref       <- Ref.of[IO, Option[Board.BoardData]](Some(initialBoardData))
      gameBoard <- Board[IO](initialBoardData, ref)
      state     <- gameBoard.read()
    } yield expect(state == initialBoardData)
  }

  test("Board should update state correctly") {
    val initialBoardData = Models.expectedBoardData
    val updatedBoardData = Models.updatedBoardData

    for {
      ref       <- Ref.of[IO, Option[Board.BoardData]](Some(initialBoardData))
      gameBoard <- Board[IO](initialBoardData, ref)
      _         <- gameBoard.update(updatedBoardData)
      state     <- gameBoard.read()
    } yield expect(state == updatedBoardData)
  }

  test("Board should delete state correctly") {
    val initialBoardData = Models.expectedBoardData

    for {
      ref         <- Ref.of[IO, Option[Board.BoardData]](Some(initialBoardData))
      gameBoard   <- Board[IO](initialBoardData, ref)
      _           <- gameBoard.delete()
      isStateless <- gameBoard.read().attempt.map(_.isLeft)
    } yield expect(isStateless)
  }

}
