package com.theproductcollectiveco.play4s

import cats.effect.IO
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.core.Orchestrator
import com.theproductcollectiveco.play4s.game.sudoku.parser.{TraceClient, GoogleCloudClient}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.game.sudoku.parser.WebSudokuExtractor
import org.http4s.ember.client.EmberClientBuilder
import scala.util.Random

object WebSudokuExtractorSpec extends SimpleIOSuite with Checkers {

  test(
    "WebSudokuExtractor should fetch valid, random boards of varying complexities to be solved."
  ) {
    given Logger[IO]  = Slf4jLogger.getLogger[IO]
    given Metrics[IO] = Metrics[IO]
    val traceParser   = TraceClient[IO]
    val imageParser   = GoogleCloudClient[IO]
    val orchestrator  = Orchestrator[IO](traceParser, imageParser)

    EmberClientBuilder
      .default[IO]
      .build
      .use: client =>
        for {
          gameGenerator                      <- IO(WebSudokuExtractor[IO](client))
          puzzleNumber                       <- IO(Random.between(0L, 10000000001L))
          (solution, validatedStartingTrace) <- gameGenerator.fetchTxtRepresentation(puzzleNumber)
          solutions                          <- IntegrationSpec.parProcessSerialized(validatedStartingTrace :: Nil, orchestrator)
          _                                  <- Logger[IO].debug(s"Fetched puzzle processed with solution: $solutions")
          actual                              = solutions.head.map(_.boardState.value.flatten.mkString)
        } yield expect(actual.exists(_.equals(solution)))
  }

}
