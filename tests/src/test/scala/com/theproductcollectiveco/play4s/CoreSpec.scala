package com.theproductcollectiveco.play4s

import cats.effect.IO
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Search, Orchestrator}
import com.theproductcollectiveco.play4s.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.game.sudoku.{BoardState, NoSolutionFoundError}
import com.theproductcollectiveco.play4s.SpecKit.SharedInstances.given

object CoreSpec extends SimpleIOSuite with Checkers {

  test("Orchestrator should parse resource correctly") {
    forall(orchestratorGen) { orchestrator =>
      orchestrator
        .processTrace("trace.txt")
        .map: lines =>
          expect(lines.nonEmpty)
    }
  }

  test("Orchestrator should parse line correctly") {
    forall(Gen.zip(boardGen, orchestratorGen)) { case (initialState, orchestrator) =>
      val line = initialState.value.flatten.mkString
      orchestrator
        .processLine(line)
        .map(parsedState => expect(parsedState.value == initialState.value))
    }
  }

  test("Search should verify Sudoku constraints correctly") {
    forall(searchGen) { search =>
      for {
        valid   <- IO(search.verify(BoardState(SpecKit.Fixtures.initialBoardState), 0, 2, 4))
        invalid <- IO(search.verify(BoardState(SpecKit.Fixtures.initialBoardState), 0, 2, 5))
      } yield expect(valid) and expect(!invalid)
    }
  }

  test("ConstraintPropagationAlgorithm.solve should find a solution for any solvable board") {
    forall(Gen.zip(boardGen, orchestratorGen)) { case (initialState, orchestrator) =>
      for {
        gameBoard <- orchestrator.createBoard(BoardState(initialState.value))
        solved    <-
          Metrics.make[IO].flatMap { metrics =>
            given Metrics[IO] = metrics
            ConstraintPropagationAlgorithm.make[IO].solve(gameBoard, Search.make)
          }
      } yield expect(solved.isDefined)
    }
  }

  test("BacktrackingAlgorithm.Operations.loop should solve Sudoku correctly") {
    forall(Gen.zip(boardGen, searchGen)) { case (initialState, search) =>
      IO.fromOption(
        BacktrackingAlgorithm.Operations.loop(
          initialState,
          search,
          search.fetchEmptyCells(initialState),
          (1 to initialState.value.size).toList,
        )
      )(NoSolutionFoundError("Expected a solved board, but got None"))
        .map: actual =>
          expect(search.verifyBoard(actual))
    }
  }

  test("BacktrackingAlgorithm.Operations.loop should solve any generated solvable board - only check that solution exists") {
    forall(Gen.zip(boardGen, searchGen)) { case (initialState, search) =>
      expect(
        BacktrackingAlgorithm.Operations
          .loop(
            initialState,
            search,
            search.fetchEmptyCells(initialState),
            (1 to initialState.value.size).toList,
          )
          .isDefined
      )
    }
  }

}
