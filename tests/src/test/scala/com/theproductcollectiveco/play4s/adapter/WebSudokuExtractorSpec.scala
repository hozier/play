package com.theproductcollectiveco.play4s.adapter

import cats.effect.IO
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.core.CoreIntegrationSpec.solve
import com.theproductcollectiveco.play4s.game.sudoku.parser.WebSudokuExtractor
import com.theproductcollectiveco.play4s.tools.SpecKit.Generators.*
import com.theproductcollectiveco.play4s.tools.SpecKit.SharedInstances.given
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger
import weaver.{SimpleIOSuite, *}
import weaver.scalacheck.Checkers

import scala.util.Random

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
              solutions                          <-
                Metrics.make[IO].flatMap { metrics =>
                  given Metrics[IO] = metrics
                  solve(validatedStartingTrace :: Nil, orchestrator)
                }
              _                                  <- Logger[IO].debug(s"Fetched puzzle processed with solution: $solutions")
              actual                              = solutions.headOption.flatMap(_.map(_.boardState.value.flatten.mkString))
            yield expect(actual.contains(solution))
          }
      }
