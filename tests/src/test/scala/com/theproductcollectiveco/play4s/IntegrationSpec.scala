package com.theproductcollectiveco.play4s

import cats.Parallel
import cats.effect.{IO, Async}
import cats.effect.std.Console
import cats.implicits.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, Orchestrator, Search}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{TraceClient, GoogleCloudVisionClient}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object IntegrationSpec extends SimpleIOSuite with Checkers {

  def sharedProcess[F[_]: Logger: Parallel: Async: Console](parsedLines: List[String], orchestrator: Orchestrator[F])(using Metrics[F]) =
    parsedLines.parTraverse { line =>
      for {
        gameBoard <- orchestrator.createBoard(orchestrator.processLine(line))
        solutions <-
          orchestrator.solve(
            gameBoard,
            Search(),
            BacktrackingAlgorithm[F](),
          )
        _         <- gameBoard.delete()
      } yield solutions
    }

  test(
    "Orchestration runs with a trace entry point should correctly solve boards of different complexities and scales."
  ) {
    given Logger[IO]  = Slf4jLogger.getLogger[IO]
    given Metrics[IO] = Metrics[IO]
    val traceParser   = TraceClient[F]
    val imageParser   = GoogleCloudVisionClient[F]
    val orchestrator  = Orchestrator[IO](traceParser, imageParser)

    orchestrator
      .processTrace("trace.txt")
      .flatMap(sharedProcess(_, orchestrator))
      .flatMap { solutions =>
        Logger[IO].debug(
          s"All puzzles processed with solutions: $solutions"
        ) as expect(solutions.nonEmpty)
      }
  }

  test(
    "Orchestration runs with an image entry point should correctly solve boards of different complexities and scales."
  ) {
    given Logger[IO]  = Slf4jLogger.getLogger[IO]
    given Metrics[IO] = Metrics[IO]
    val traceParser   = TraceClient[F]
    val imageParser   = GoogleCloudVisionClient[F]
    val orchestrator  = Orchestrator[IO](traceParser, imageParser)

    orchestrator
      .fetchBytes("sudoku_test_image_v0.0.1.png")
      .flatMap(orchestrator.processImage)
      .map(_ :: Nil) // because sharedProcess expects a List type
      .flatMap(sharedProcess(_, orchestrator))
      .flatMap { solutions =>
        Logger[IO].debug(
          s"All puzzles processed with solutions: $solutions"
        ) as expect(solutions.nonEmpty)
      }
  }

}
