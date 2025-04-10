package com.theproductcollectiveco.play4s.api

import cats.implicits.*
import cats.effect.Clock
import cats.effect.std.UUIDGen
import cats.effect.kernel.MonadCancelThrow
import cats.Parallel
import cats.effect.Async
import cats.effect.std.Console
import com.theproductcollectiveco.play4s.{Play4sApi, Metrics}
import com.theproductcollectiveco.play4s.game.sudoku.{SudokuComputationSummary, GameId, Strategy}
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
    algorithms: Algorithm[F]*
  ): Play4sService[F] =
    new Play4sService[F] with Play4sApi[F]:

      override def computeSudoku(image: smithy4s.Blob): F[SudokuComputationSummary] =
        orchestrator
          .processImage(image.toArray)
          .flatMap:
            computeSudokuCore(_, clock, uuidGen, algorithms)

      override def computeSudokuDeveloperMode(trace: String): F[SudokuComputationSummary] = computeSudokuCore(trace, clock, uuidGen, algorithms)

      private def computeSudokuCore(
        trace: String,
        clock: Clock[F],
        uuidGen: UUIDGen[F],
        algorithms: Seq[Algorithm[F]],
      ): F[SudokuComputationSummary] =
        for {
          start         <- clock.monotonic
          requestedAt   <-
            clock.realTime.map: t =>
              val seconds = t.toSeconds
              val nanos   = (t - seconds.seconds).toNanos
              Timestamp(seconds, nanos.toInt)
          gameId        <- uuidGen.randomUUID.map(uuid => GameId(uuid.toString))
          gameBoard     <- orchestrator.createBoard(orchestrator.processLine(trace))
          maybeSolution <- orchestrator.solve(gameBoard, Search(), algorithms*)
          _             <- gameBoard.delete()
          end           <- clock.monotonic
          duration       = (end - start).toMillis
        } yield SudokuComputationSummary(
          id = gameId,
          duration = duration,
          requestedAt = requestedAt,
          strategies = Strategy.BACKTRACKING :: Strategy.CONSTRAINT_PROPAGATION :: Nil,
          fastestOutcome = maybeSolution.map(_.strategy),
          solution = maybeSolution.map(_.boardState),
        )

}
