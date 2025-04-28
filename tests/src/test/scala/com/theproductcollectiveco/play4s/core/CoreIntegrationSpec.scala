package com.theproductcollectiveco.play4s.core

import cats.Parallel
import cats.effect.{Async, IO}
import cats.effect.std.Console
import cats.effect.syntax.all.*
import cats.implicits.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator, Search, SolvedState}
import com.theproductcollectiveco.play4s.tools.SpecKit
import com.theproductcollectiveco.play4s.tools.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.tools.SpecKit.Operations.skipOnCI
import com.theproductcollectiveco.play4s.tools.SpecKit.SharedInstances.given
import org.typelevel.log4cats.Logger
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

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
