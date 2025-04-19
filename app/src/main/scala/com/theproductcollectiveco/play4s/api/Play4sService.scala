package com.theproductcollectiveco.play4s.api

import cats.implicits.*
import cats.effect.syntax.all.*
import cats.effect.{Clock, Async}
import cats.effect.std.UUIDGen
import cats.Parallel
import cats.effect.std.Console
import com.theproductcollectiveco.play4s.{Play4sApi, Metrics}
import com.theproductcollectiveco.play4s.game.sudoku.{SudokuComputationSummary, GameId, Strategy, ConcurrentExecutionDetails}
import com.theproductcollectiveco.play4s.game.sudoku.core.{Algorithm, Orchestrator, Search}
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp
import scala.concurrent.duration.DurationLong

object Play4sService {

  def apply[F[_]: Async: Logger: Console: Parallel: Metrics](
    clock: Clock[F],
    uuidGen: UUIDGen[F],
    orchestrator: Orchestrator[F],
    algorithms: Algorithm[F]*
  ): Play4sApi[F] =
    new Play4sApi[F]:

      override def computeSudoku(image: smithy4s.Blob): F[SudokuComputationSummary] =
        runWithEntryPoint(orchestrator.processImage(image.toArray), orchestrator, algorithms)

      override def computeSudokuDeveloperMode(trace: String): F[SudokuComputationSummary] = runWithEntryPoint(trace.pure, orchestrator, algorithms)

      private def runWithEntryPoint(
        entryPoint: F[String],
        orchestrator: Orchestrator[F],
        algorithms: Seq[Algorithm[F]],
      ): F[SudokuComputationSummary] =
        entryPoint.flatMap { trace =>
          for {
            start         <- clock.monotonic
            requestedAt   <- clock.getCurrentTimestamp
            gameId        <- uuidGen.randomUUID.map(uuid => GameId(uuid.toString))
            maybeSolution <-
              orchestrator.createBoard(orchestrator.processLine(trace)).flatMap { gameBoard =>
                orchestrator
                  .solve(gameBoard, Search(), algorithms*)
                  .guarantee(gameBoard.delete())
              }
            end           <- clock.monotonic
            duration       = (end - start).toMillis
          } yield SudokuComputationSummary(
            id = gameId,
            duration = duration,
            requestedAt = requestedAt,
            concurrentExecutionDetails =
              ConcurrentExecutionDetails(
                strategies = Strategy.BACKTRACKING :: Strategy.CONSTRAINT_PROPAGATION :: Nil,
                earliestCompleted = maybeSolution.map(_.strategy),
              ),
            solution = maybeSolution.map(_.boardState),
          )
        }

  extension [F[_]: Clock: Async](clock: Clock[F])

    def getCurrentTimestamp: F[Timestamp] =
      Clock[F].realTime.map { t =>
        val seconds = t.toSeconds
        val nanos   = (t - seconds.seconds).toNanos
        Timestamp(seconds, nanos.toInt)
      }

}
