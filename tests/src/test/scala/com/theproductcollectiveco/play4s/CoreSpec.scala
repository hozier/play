package com.theproductcollectiveco.play4s

import cats.effect.IO
import cats.Show
import cats.syntax.show.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, Search, Orchestrator}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.game.sudoku.parser.{TraceClient, GoogleCloudClient}
import com.theproductcollectiveco.play4s.shared.SpecKit.Fixtures.*
import com.theproductcollectiveco.play4s.shared.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.game.sudoku.BoardState

object CoreSpec extends SimpleIOSuite with Checkers {

  given Show[BoardState] = Show.show(_.toString)
  given Logger[IO]       = Slf4jLogger.getLogger[IO]
  given Metrics[IO]      = Metrics[IO]
  val traceParser        = TraceClient[F]
  val imageParser        = GoogleCloudClient[F]
  val orchestrator       = Orchestrator[IO](traceParser, imageParser)

  test("Orchestrator should parse resource correctly") {
    orchestrator
      .processTrace("trace.txt")
      .map: lines =>
        expect(lines.nonEmpty)
  }

  test("Orchestrator should parse line correctly") {
    val line       = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    val parseState = orchestrator.processLine(line)
    IO(expect(parseState.value == initialBoardState))
  }

  test("Algorithm.solve should solve any generated solvable board - only check that solution exists") {
    forall(boardGen) { initialState =>
      for {
        gameBoard <- orchestrator.createBoard(BoardState(initialState.value))
        solved    <- BacktrackingAlgorithm[IO]().solve(gameBoard, Search())
      } yield expect(solved.isDefined)
    }
  }

  test("Search should verify Sudoku constraints correctly") {
    val initialState = BoardState(initialBoardState)
    val search       = Search()

    for {
      valid   <- IO(search.verify(initialState, 0, 2, 4))
      invalid <- IO(search.verify(initialState, 0, 2, 5))
    } yield expect(valid) and expect(!invalid)
  }

  test("BacktrackingAlgorithm.Operations.loop should solve Sudoku correctly") {
    val initialState = BoardState(initialBoardState)
    val search       = Search()
    val result       =
      BacktrackingAlgorithm.Operations.loop(
        initialState,
        search,
        search.fetchEmptyCells(initialState),
        1.to(initialState.value.size),
      )

    IO(expect(result.contains(BoardState(updatedBoardState))))
  }

  test("BacktrackingAlgorithm.Operations.loop should solve any generated solvable board - only check that solution exists") {
    forall(boardGen) { initialState =>
      val search = Search()
      expect(
        BacktrackingAlgorithm.Operations
          .loop(
            initialState,
            search,
            search.fetchEmptyCells(initialState),
            1.to(initialState.value.size),
          )
          .isDefined
      )
    }
  }

}
