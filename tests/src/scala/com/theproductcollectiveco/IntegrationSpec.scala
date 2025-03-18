package com.theproductcollectiveco.test

import cats.effect.IO
import cats.implicits.*
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.Metrics
import com.theproductcollectiveco.store.Board
import com.theproductcollectiveco.games.sudoku.clients.{Orchestration, BacktrackingAlgorithm, Search}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object IntegrationSpec extends SimpleIOSuite with Checkers {

  test(
    "Orchestrated runs should correctly solve boards of different complexities and scales."
  ) {
    given Logger[IO]  = Slf4jLogger.getLogger[IO]
    given Metrics[IO] = Metrics[IO]
    val orchestration = Orchestration[IO]()
    orchestration
      .parseResource("trace.txt")
      .flatMap {
        _.parTraverse { line =>
          for {
            store     <- cats.effect.Ref.of[IO, Option[Board.BoardData]](None)
            boardData  = orchestration.parseLine(line)
            metrics    = Metrics[IO]
            gameBoard <- Board[IO](boardData, store)
            solutions <-
              orchestration.solve(
                gameBoard,
                Search(),
                BacktrackingAlgorithm[IO](),
              )
            _         <- gameBoard.delete()
          } yield solutions
        }
      }
      .flatMap { solutions =>
        Logger[IO].debug(
          s"All puzzles processed with solutions: $solutions"
        ) as expect(solutions.nonEmpty)
      }
  }

}
