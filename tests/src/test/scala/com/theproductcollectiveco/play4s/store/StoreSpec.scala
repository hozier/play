package com.theproductcollectiveco.play4s.store

import cats.effect.{IO, Ref}
import com.theproductcollectiveco.play4s.game.sudoku.{BoardNotCreatedError, BoardState, InitialStateSettingError}
import com.theproductcollectiveco.play4s.tools.SpecKit.Fixtures.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

object BoardSpec extends SimpleIOSuite with Checkers {

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("Board should read initial state correctly") {
    val initialState = BoardState(initialBoardState)

    for {
      ref       <- Ref.of[IO, Option[BoardState]](None)
      gameBoard <- Board[IO](initialState, ref)
      state     <- gameBoard.read()
    } yield expect(state == initialState)
  }

  test("Board should update state correctly") {
    val updatedState = BoardState(updatedBoardState)

    for {
      ref       <- Ref.of[IO, Option[BoardState]](None)
      gameBoard <- Board[IO](BoardState(initialBoardState), ref)
      _         <- gameBoard.update(updatedState)
      state     <- gameBoard.read()
    } yield expect(state == updatedState)
  }

  test("Board should delete state correctly") {
    for {
      ref         <- Ref.of[IO, Option[BoardState]](None)
      gameBoard   <- Board[IO](BoardState(initialBoardState), ref)
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
