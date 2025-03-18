package com.theproductcollectiveco.test

import cats.effect.IO
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.Metrics
import com.theproductcollectiveco.store.Board
import com.theproductcollectiveco.games.sudoku.clients.{Orchestration, BacktrackingAlgorithm, Search}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

object ClientsSpec extends SimpleIOSuite with Checkers {

  given Logger[IO]  = Slf4jLogger.getLogger[IO]
  given Metrics[IO] = Metrics[IO]

  test("Orchestration should parse resource correctly") {
    val orchestration = Orchestration[IO]()
    val resource      = "trace.txt"

    for {
      lines <- orchestration.parseResource(resource)
    } yield expect(lines.nonEmpty)
  }

  test("Orchestration should parse line correctly") {
    val orchestration = Orchestration[IO]()
    val line          = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    val boardData         = orchestration.parseLine(line)
    val expectedBoardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    IO(expect(boardData == expectedBoardData))
  }

  test("Algorithm should solve Sudoku correctly") {
    val boardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    val board     =
      cats.effect.Ref
        .of[IO, Option[Board.BoardData]](None)
        .flatMap: ref =>
          Board[IO](
            boardData,
            ref,
          )
    val search    = Search()
    val algorithm = BacktrackingAlgorithm[IO]()

    for {
      gameBoard <- board
      solved    <- algorithm.solve(gameBoard, search)
    } yield expect(solved)
  }

  test("Search should verify Sudoku constraints correctly") {
    val boardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )
    val search    = Search()

    for {
      valid   <- IO(search.verify(boardData, 0, 2, 4))
      invalid <- IO(search.verify(boardData, 0, 2, 5))
    } yield expect(valid) and expect(!invalid)
  }

  test("BacktrackingAlgorithm.Operations.loop should solve Sudoku correctly") {
    val boardData =
      Vector(
        Vector(5, 3, 0, 0, 7, 0, 0, 0, 0),
        Vector(6, 0, 0, 1, 9, 5, 0, 0, 0),
        Vector(0, 9, 8, 0, 0, 0, 0, 6, 0),
        Vector(8, 0, 0, 0, 6, 0, 0, 0, 3),
        Vector(4, 0, 0, 8, 0, 3, 0, 0, 1),
        Vector(7, 0, 0, 0, 2, 0, 0, 0, 6),
        Vector(0, 6, 0, 0, 0, 0, 2, 8, 0),
        Vector(0, 0, 0, 4, 1, 9, 0, 0, 5),
        Vector(0, 0, 0, 0, 8, 0, 0, 7, 9),
      )

    val search = Search()

    val result =
      BacktrackingAlgorithm.Operations.loop(
        boardData,
        search,
        search.fetchEmptyCells(boardData),
      )

    val expectedSolvedBoard =
      Vector(
        Vector(5, 3, 4, 6, 7, 8, 9, 1, 2),
        Vector(6, 7, 2, 1, 9, 5, 3, 4, 8),
        Vector(1, 9, 8, 3, 4, 2, 5, 6, 7),
        Vector(8, 5, 9, 7, 6, 1, 4, 2, 3),
        Vector(4, 2, 6, 8, 5, 3, 7, 9, 1),
        Vector(7, 1, 3, 9, 2, 4, 8, 5, 6),
        Vector(9, 6, 1, 5, 3, 7, 2, 8, 4),
        Vector(2, 8, 7, 4, 1, 9, 6, 3, 5),
        Vector(3, 4, 5, 2, 8, 6, 1, 7, 9),
      )

    IO(expect(result.contains(expectedSolvedBoard)))
  }

}
