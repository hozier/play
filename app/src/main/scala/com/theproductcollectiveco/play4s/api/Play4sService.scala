package com.theproductcollectiveco.play4s.api

import cats.implicits.*
import cats.effect.Clock
import cats.effect.std.UUIDGen
import cats.effect.kernel.MonadCancelThrow
import cats.Parallel
import cats.effect.Async
import cats.effect.std.Console
import com.theproductcollectiveco.play4s.{Play4sApi, Metrics}
import com.theproductcollectiveco.play4s.game.sudoku.{SudokuComputationSummary, Algorithm as Strategy, GameId}
import com.theproductcollectiveco.play4s.game.sudoku.core.{Algorithm, Orchestrator, Search}
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp
import scala.concurrent.duration.DurationLong

trait Play4sService[F[_]] extends Play4sApi[F] {}

object Play4sService {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console: Parallel: Metrics](
    clock: Clock[F],
    uuidGen: UUIDGen[F],
    orchestrator: Orchestrator[F],
    algorithm: Algorithm[F],
  ): Play4sService[F] =
    new Play4sService[F] with Play4sApi[F]:
      override def computeSudoku(image: smithy4s.Blob): F[SudokuComputationSummary] =
        for {
          start       <- clock.monotonic
          requestedAt <-
            clock.realTime.map: t =>
              val seconds = t.toSeconds
              val nanos   = (t - seconds.seconds).toNanos
              Timestamp(seconds, nanos.toInt)
          gameId      <- uuidGen.randomUUID.map(uuid => GameId(uuid.toString))
          parsedLines <- orchestrator.processImage(image.toArray).map(_ :: Nil)
          headOption  <-
            parsedLines
              .parTraverse: trace =>
                for {
                  gameBoard <- orchestrator.createBoard(orchestrator.processLine(trace))
                  solutions <-
                    orchestrator.solve(
                      gameBoard,
                      Search(),
                      algorithm,
                    )
                  _         <- gameBoard.delete()
                } yield solutions
              .map:
                _.head
          end         <- clock.monotonic
          duration     = (end - start).toMillis
        } yield SudokuComputationSummary(
          id = gameId,
          strategy = Strategy(algorithm.getClass().getName()),
          duration = duration,
          requestedAt = requestedAt,
          solution = headOption,
        )

}
