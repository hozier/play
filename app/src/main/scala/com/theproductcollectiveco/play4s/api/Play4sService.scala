package com.theproductcollectiveco.play4s.api

import cats.Parallel
import cats.effect.{Async, Clock}
import cats.effect.std.{Console, UUIDGen}
import cats.effect.syntax.all.*
import cats.implicits.*
import com.theproductcollectiveco.play4s.{Metrics, Play4sApi}
import com.theproductcollectiveco.play4s.game.sudoku.{
  AlgorithmUsage,
  ConcurrentExecutionDetails,
  EmptyCellHints,
  EmptyCellHintsMetadata,
  GameId,
  GameMetrics,
  Strategy,
  SudokuComputationSummary,
}
import com.theproductcollectiveco.play4s.game.sudoku.core.{asHints, queryDomain, Algorithm, Orchestrator, Search}
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp

import scala.concurrent.duration.DurationLong

object Play4sService {

  def make[F[_]: Async: Logger: Console: Parallel: Metrics](
    clock: Clock[F],
    uuidGen: UUIDGen[F],
    orchestrator: Orchestrator[F],
    algorithms: Algorithm[F]*
  ): Play4sApi[F] =
    new Play4sApi[F]:

      override def getSudokuHints(trace: String, hintCount: Option[Int]): F[EmptyCellHints] =
        orchestrator.processLine(trace).flatMap {
          _.queryDomain(Search.make, hintCount)
            .map { case (domain, size) => EmptyCellHints(domain.asHints, EmptyCellHintsMetadata(size)) }
        }

      override def computeSudoku(image: smithy4s.Blob): F[SudokuComputationSummary] = computeWithEntryPoint(orchestrator.processImage(image.toArray))

      override def computeSudokuDeveloperMode(trace: String): F[SudokuComputationSummary] = computeWithEntryPoint(trace.pure)

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
                  .solve(gameBoard, Search.make, algorithms*)
                  .guarantee(gameBoard.delete())
                  .flatTap {
                    _.map(_.strategy.value).mkString.pure
                      .flatMap:
                        Metrics[F].recordHistogram("algorithmsUsage", _)
                  }
              }
            end           <- clock.monotonic
            duration       = (end - start).toMillis
            currentMax    <- Metrics[F].getGauge("maxSolveTime")
            currentMin    <- Metrics[F].getGauge("minSolveTime")
            _             <- Metrics[F].updateGauge("averageSolveTime", duration.toDouble)
            _             <- Metrics[F].updateGauge("maxSolveTime", math.max(duration.toDouble, currentMax))
            _             <- Metrics[F].incrementCounter("totalPuzzlesSolved")
            _             <-
              Async[F].ifM(Metrics[F].getGauge("minSolveTime").map(_ == -1.0))(
                Metrics[F].updateGauge("minSolveTime", duration.toDouble),
                Metrics[F].updateGauge("minSolveTime", math.min(duration.toDouble, currentMin)),
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
          totalPuzzlesSolved   <- Metrics[F].getCounter("totalPuzzlesSolved")
          averageSolveTime     <- Metrics[F].getGauge("averageSolveTime")
          maxSolveTime         <- Metrics[F].getGauge("maxSolveTime")
          minSolveTime         <- Metrics[F].getGauge("minSolveTime")
          algorithmsUsageStats <- Metrics[F].getHistogram("algorithmsUsage")
        } yield GameMetrics(
          totalPuzzlesSolved,
          averageSolveTime,
          maxSolveTime,
          minSolveTime,
          algorithmsUsage =
            (Strategy.BACKTRACKING :: Strategy.CONSTRAINT_PROPAGATION :: Nil).map(alg => AlgorithmUsage(alg, algorithmsUsageStats(alg.value))),
        )

  extension [F[_]: Clock: Async](clock: Clock[F])

    def getCurrentTimestamp: F[Timestamp] =
      Clock[F].realTime.map { t =>
        val seconds = t.toSeconds
        val nanos   = (t - seconds.seconds).toNanos
        Timestamp(seconds, nanos.toInt)
      }

}
