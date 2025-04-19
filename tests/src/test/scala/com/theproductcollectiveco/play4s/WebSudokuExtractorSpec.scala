package com.theproductcollectiveco.play4s

import cats.effect.IO
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers
import com.theproductcollectiveco.play4s.game.sudoku.parser.WebSudokuExtractor
import org.typelevel.log4cats.Logger
import org.http4s.ember.client.EmberClientBuilder
import scala.util.Random
import weaver.*
import com.theproductcollectiveco.play4s.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.SpecKit.SharedInstances.given

object WebSudokuExtractorSpec extends SimpleIOSuite with Checkers:

  test("WebSudokuExtractor should fetch valid, random boards of varying complexities to be solved."):
    ignore("Scaling down due to external rate-limitings") <*
      forall(orchestratorGen) { orchestrator =>
        EmberClientBuilder
          .default[IO]
          .build
          .use { client =>
            for
              gameGenerator                      <- IO(WebSudokuExtractor[IO](client))
              puzzleNumber                       <- IO(Random.between(0L, 10000000001L))
              (solution, validatedStartingTrace) <- gameGenerator.fetchTxtRepresentation(puzzleNumber)
              solutions                          <- IntegrationSpec.solve(validatedStartingTrace :: Nil, orchestrator)
              _                                  <- Logger[IO].debug(s"Fetched puzzle processed with solution: $solutions")
              actual                              = solutions.headOption.flatMap(_.map(_.boardState.value.flatten.mkString))
            yield expect(actual.contains(solution))
          }
      }
