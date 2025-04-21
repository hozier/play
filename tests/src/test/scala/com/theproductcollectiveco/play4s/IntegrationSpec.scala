package com.theproductcollectiveco.play4s

import cats.Parallel
import cats.effect.{IO, Async}
import cats.effect.std.Console
import cats.implicits.*
import cats.effect.syntax.all.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, ConstraintPropagationAlgorithm, Orchestrator, Search, SolvedState}
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.SpecKit.Operations.skipOnCI
import com.theproductcollectiveco.play4s.SpecKit.SharedInstances.given

object IntegrationSpec extends SimpleIOSuite with Checkers {

  def runTest[F[_]: Async: Parallel: Console](
    entryPoint: F[List[String]],
    orchestrator: Orchestrator[F],
    metrics: Metrics[F],
  )(using Logger[F]) =
    entryPoint
      .flatMap(solve(_, orchestrator, metrics))
      .flatMap { solutions =>
        Logger[F].debug(s"All puzzles processed with solutions: $solutions") *>
          expect(solutions.forall(_.isDefined)).pure
      }

  def solve[F[_]: Parallel: Async: Console](
    inputs: List[String],
    orchestrator: Orchestrator[F],
    metrics: Metrics[F],
  )(using Logger[F]): F[List[Option[SolvedState]]] =
    inputs.parTraverse { line =>
      orchestrator.processLine(line).flatMap {
        orchestrator.createBoard(_).flatMap { gameBoard =>
          orchestrator
            .solve(gameBoard, Search(), BacktrackingAlgorithm[F](metrics), ConstraintPropagationAlgorithm[F](metrics))
            .guarantee(gameBoard.delete())
        }
      }
    }

  test("Solve boards from trace file.") {
    forall(orchestratorGen) { orchestrator =>
      Metrics.make[IO].flatMap {
        runTest[IO](
          orchestrator.processTrace("trace.txt"),
          orchestrator,
          _,
        )
      }
    }
  }

  test("Solve boards from image file.") {
    forall(orchestratorGen) { orchestrator =>
      Metrics.make[IO].flatMap {
        skipOnCI *> runTest(
          orchestrator.fetchBytes("sudoku_test_image_v0.0.1.png").flatMap(orchestrator.processImage).map(List(_)),
          orchestrator,
          _,
        )
      }
    }
  }

}
