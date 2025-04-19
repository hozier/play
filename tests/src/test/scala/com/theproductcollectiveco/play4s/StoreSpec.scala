package com.theproductcollectiveco.play4s

import cats.effect.{IO, Ref}
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.store.Board
import com.theproductcollectiveco.play4s.shared.SpecKit.Fixtures
import com.theproductcollectiveco.play4s.game.sudoku.{InitialStateSettingError, BoardNotCreatedError, BoardState}

object StoreSpec extends SimpleIOSuite with Checkers {

  given Logger[IO]  = Slf4jLogger.getLogger[IO]
  given Metrics[IO] = Metrics[IO]

  test("Board should read initial state correctly") {
    val initialState = BoardState(Fixtures.initialBoardState)

    for {
      ref       <- Ref.of[IO, Option[BoardState]](None)
      gameBoard <- Board[IO](initialState, ref)
      state     <- gameBoard.read()
    } yield expect(state == initialState)
  }

  test("Board should update state correctly") {
    val updatedBoardState = BoardState(Fixtures.updatedBoardState)

    for {
      ref       <- Ref.of[IO, Option[BoardState]](None)
      gameBoard <- Board[IO](BoardState(Fixtures.initialBoardState), ref)
      _         <- gameBoard.update(updatedBoardState)
      state     <- gameBoard.read()
    } yield expect(state == updatedBoardState)
  }

  test("Board should delete state correctly") {
    for {
      ref         <- Ref.of[IO, Option[BoardState]](None)
      gameBoard   <- Board[IO](BoardState(Fixtures.initialBoardState), ref)
      _           <- gameBoard.delete()
      isStateless <-
        gameBoard
          .read()
          .attempt
          .map:
            case (Left(InitialStateSettingError(_)) | Left(BoardNotCreatedError(_))) => true

            /** Either ran into an unexpected runtime error or returned a state that should have been deleted. */
            case (Left(_) | Right(_)) => false
    } yield expect(isStateless)
  }

}
