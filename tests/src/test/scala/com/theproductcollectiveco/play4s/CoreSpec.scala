package com.theproductcollectiveco.play4s

import cats.effect.IO
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Search, Orchestrator}
import com.theproductcollectiveco.play4s.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.game.sudoku.BoardState
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
      val line       = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
      val parseState = orchestrator.processLine(line)
      IO(expect(parseState.value == initialState.value))
    }
  }

  test("Search should verify Sudoku constraints correctly") {
    forall(Gen.zip(boardGen, searchGen)) { case (initialState, search) =>
      for {
        valid   <- IO(search.verify(initialState, 0, 2, 4))
        invalid <- IO(search.verify(initialState, 0, 2, 5))
      } yield expect(valid) and expect(!invalid)
    }
  }

  test("ConstraintPropagationAlgorithm.solve should find a solution for any solvable board") {
    forall(Gen.zip(boardGen, orchestratorGen)) { case (initialState, orchestrator) =>
      for {
        gameBoard <- orchestrator.createBoard(BoardState(initialState.value))
        solved    <- ConstraintPropagationAlgorithm[IO]().solve(gameBoard, Search())
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
          1.to(initialState.value.size),
        )
      )(new RuntimeException("Expected a solved board, but got None"))
        .map: state =>
          expect(state == BoardState(SpecKit.Fixtures.updatedBoardState))
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
            1.to(initialState.value.size),
          )
          .isDefined
      )
    }
  }

}
