package com.theproductcollectiveco.play4s

import cats.effect.IO
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.store.Board
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, Search, Orchestrator}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.game.sudoku.parser.{TraceClient, GoogleCloudVisionClient}
import com.theproductcollectiveco.play4s.shared.Models

object CoreSpec extends SimpleIOSuite with Checkers {

  given Logger[IO]  = Slf4jLogger.getLogger[IO]
  given Metrics[IO] = Metrics[IO]
  val traceParser   = TraceClient[F]
  val imageParser   = GoogleCloudVisionClient[F]
  val orchestrator  = Orchestrator[IO](traceParser, imageParser)

  test("Orchestrator should parse resource correctly") {
    val resource = "trace.txt"

    for {
      lines <- orchestrator.processTrace(resource)
    } yield expect(lines.nonEmpty)
  }

  test("Orchestrator should parse line correctly") {
    val line              = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    val boardData         = orchestrator.processLine(line)
    val expectedBoardData = Models.expectedBoardData
    IO(expect(boardData == expectedBoardData))
  }

  test("Algorithm should solve Sudoku correctly") {
    val boardData = Models.expectedBoardData
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
    val boardData = Models.expectedBoardData
    val search    = Search()

    for {
      valid   <- IO(search.verify(boardData, 0, 2, 4))
      invalid <- IO(search.verify(boardData, 0, 2, 5))
    } yield expect(valid) and expect(!invalid)
  }

  test("BacktrackingAlgorithm.Operations.loop should solve Sudoku correctly") {
    val boardData = Models.expectedBoardData
    val search    = Search()
    val result    =
      BacktrackingAlgorithm.Operations.loop(
        boardData,
        search,
        search.fetchEmptyCells(boardData),
      )

    val expectedSolvedBoard = Models.updatedBoardData

    IO(expect(result.contains(expectedSolvedBoard)))
  }

}
