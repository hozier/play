package com.theproductcollectiveco.play4s.api

import cats.implicits.*
import cats.effect.syntax.all.*
import cats.effect.{Clock, Async}
import cats.effect.std.UUIDGen
import cats.Parallel
import cats.effect.std.Console
import com.theproductcollectiveco.play4s.{Play4sApi, Metrics}
import com.theproductcollectiveco.play4s.game.sudoku.{
  SudokuComputationSummary,
  GameId,
  Strategy,
  ConcurrentExecutionDetails,
  EmptyCellHints,
  EmptyCellHintsMetadata,
  GameMetrics,
  AlgorithmUsage,
}
import com.theproductcollectiveco.play4s.game.sudoku.core.{Algorithm, Orchestrator, Search, queryDomain, asHints}
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp
import scala.concurrent.duration.DurationLong

object Play4sService {

  def apply[F[_]: Async: Logger: Console: Parallel](
    clock: Clock[F],
    uuidGen: UUIDGen[F],
    orchestrator: Orchestrator[F],
    metrics: Metrics[F],
    algorithms: Algorithm[F]*
  ): Play4sApi[F] =
    new Play4sApi[F]:

      override def getSudokuHints(trace: String, hintCount: Option[Int]): F[EmptyCellHints] =
        orchestrator.processLine(trace).flatMap {
          _.queryDomain(Search(), metrics, hintCount)
            .map { case (domain, size) => EmptyCellHints(domain.asHints, EmptyCellHintsMetadata(size)) }
        }

      override def computeSudoku(image: smithy4s.Blob): F[SudokuComputationSummary] = computeWithEntryPoint(orchestrator.processImage(image.toArray))

      override def computeSudokuDeveloperMode(trace: String): F[SudokuComputationSummary] =
        metrics.incrementCounter("totalPuzzlesSolved") *>
          computeWithEntryPoint(trace.pure)

      private def computeWithEntryPoint(entryPoint: F[String]): F[SudokuComputationSummary] =
        entryPoint.flatMap { trace =>
          for
            start         <- clock.monotonic
            requestedAt   <- clock.getCurrentTimestamp
            gameId        <- uuidGen.randomUUID.map(uuid => GameId(uuid.toString))
            state         <- orchestrator.processLine(trace)
            maybeSolution <-
              orchestrator.createBoard(state).flatMap { gameBoard =>
                orchestrator
                  .solve(gameBoard, Search(), algorithms*)
                  .guarantee(gameBoard.delete())
                  .flatTap {
                    _.map(_.strategy.value).mkString.pure
                      .flatMap:
                        metrics.recordHistogram("algorithmsUsage", _)
                  }
              }
            end           <- clock.monotonic
            duration       = (end - start).toMillis
            currentMax    <- metrics.getGauge("maxSolveTime")
            currentMin    <- metrics.getGauge("minSolveTime")
            _             <- metrics.updateGauge("averageSolveTime", duration.toDouble)
            _             <- metrics.updateGauge("maxSolveTime", math.max(duration.toDouble, currentMax))
            _             <-
              Async[F].ifM(metrics.getGauge("minSolveTime").map(_ == -1.0))(
                metrics.updateGauge("minSolveTime", duration.toDouble),
                metrics.updateGauge("minSolveTime", math.min(duration.toDouble, currentMin)),
              )
          yield SudokuComputationSummary(
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

      override def getSudokuMetrics(): F[GameMetrics] =
        for {
          totalPuzzlesSolved   <- metrics.getCounter("totalPuzzlesSolved")
          averageSolveTime     <- metrics.getGauge("averageSolveTime")
          maxSolveTime         <- metrics.getGauge("maxSolveTime")
          minSolveTime         <- metrics.getGauge("minSolveTime")
          algorithmsUsageStats <- metrics.getHistogram("algorithmsUsage")
        } yield GameMetrics(
          totalPuzzlesSolved,
          averageSolveTime,
          maxSolveTime,
          minSolveTime,
          algorithmsUsage =
            AlgorithmUsage(Strategy.BACKTRACKING, algorithmsUsageStats(Strategy.BACKTRACKING.value)) ::
              AlgorithmUsage(Strategy.CONSTRAINT_PROPAGATION, algorithmsUsageStats(Strategy.CONSTRAINT_PROPAGATION.value)) ::
              Nil,
        )

  extension [F[_]: Clock: Async](clock: Clock[F])

    def getCurrentTimestamp: F[Timestamp] =
      Clock[F].realTime.map { t =>
        val seconds = t.toSeconds
        val nanos   = (t - seconds.seconds).toNanos
        Timestamp(seconds, nanos.toInt)
      }

}
