package com.theproductcollectiveco.play4s

import cats.Parallel
import cats.effect.{IO, Async}
import cats.effect.std.Console
import cats.implicits.*
import cats.effect.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator, Search}
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.SpecKit.Operations.*
import com.theproductcollectiveco.play4s.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.SpecKit.SharedInstances.given

object IntegrationSpec extends SimpleIOSuite with Checkers {

  def runTest[F[_]: Async: Parallel: Console](
    entryPoint: F[List[String]],
    orchestrator: Orchestrator[F],
  )(using Metrics[F], Logger[F]) =
    entryPoint
      .flatMap(solve(_, orchestrator))
      .flatMap { solutions =>
        Logger[F].debug(s"All puzzles processed with solutions: $solutions") *>
          expect(solutions.forall(_.isDefined)).pure
      }

  def solve[F[_]: Parallel: Async: Console](
    inputs: List[String],
    orchestrator: Orchestrator[F],
  )(using Metrics[F], Logger[F]) =
    inputs.parTraverse { line =>
      orchestrator.createBoard(orchestrator.processLine(line)).flatMap { gameBoard =>
        orchestrator
          .solve(gameBoard, Search(), BacktrackingAlgorithm[F](), ConstraintPropagationAlgorithm[F]())
          .guarantee(gameBoard.delete())
      }
    }

  test("Solve boards from trace file.") {
    forall(orchestratorGen) { orchestrator =>
      runTest(
        orchestrator.processTrace("trace.txt"),
        orchestrator,
      )
    }
  }

  test("Solve boards from image file.") {
    forall(orchestratorGen) { orchestrator =>
      skipOnCI *> runTest(
        orchestrator.fetchBytes("sudoku_test_image_v0.0.1.png").flatMap(orchestrator.processImage).map(List(_)),
        orchestrator,
      )
    }
  }

}
