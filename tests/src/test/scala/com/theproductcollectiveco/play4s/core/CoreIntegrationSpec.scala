package com.theproductcollectiveco.play4s.core

import cats.Parallel
import cats.effect.{IO, Async}
import cats.effect.std.Console
import cats.implicits.*
import cats.effect.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator, Search, SolvedState}
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.tools.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.tools.SpecKit.Operations.skipOnCI
import com.theproductcollectiveco.play4s.tools.SpecKit.SharedInstances.given
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.tools.SpecKit

object CoreIntegrationSpec extends SimpleIOSuite with Checkers {

  def runTest[F[_]: Async: Parallel: Console: Logger: Metrics](
    entryPoint: F[List[String]],
    orchestrator: Orchestrator[F],
  ) =
    entryPoint
      .flatMap(solve(_, orchestrator))
      .flatMap { solutions =>
        Logger[F].debug(s"All puzzles processed with solutions: $solutions") *>
          expect(solutions.forall(_.isDefined)).pure
      }

  def solve[F[_]: Parallel: Async: Console: Logger: Metrics](
    inputs: List[String],
    orchestrator: Orchestrator[F],
  ): F[List[Option[SolvedState]]] =
    inputs.parTraverse { line =>
      orchestrator.processLine(line).flatMap {
        orchestrator.createBoard(_).flatMap { gameBoard =>
          orchestrator
            .solve(gameBoard, Search.make, BacktrackingAlgorithm.make[F], ConstraintPropagationAlgorithm.make[F])
            .guarantee(gameBoard.delete())
        }
      }
    }

  test("Solve boards from trace file.") {
    forall(orchestratorGen) { orchestrator =>
      Metrics.make[IO].flatMap { metrics =>
        given Metrics[IO] = metrics
        runTest[IO](
          orchestrator.processTrace("trace.txt"),
          orchestrator,
        )
      }
    }
  }

  test("Solve boards from image file.") {
    forall(orchestratorGen) { orchestrator =>
      Metrics.make[IO].flatMap { metrics =>
        given Metrics[IO] = metrics
        skipOnCI *> runTest(
          orchestrator.fetchResourceBytes("sudoku_test_image_v0.0.1.png").flatMap(orchestrator.processImage).map(List(_)),
          orchestrator,
        )
      }
    }
  }

}
