package com.theproductcollectiveco.play4s

import cats.implicits.*
import cats.effect.Clock
import cats.effect.std.UUIDGen
import cats.effect.kernel.MonadCancelThrow
import cats.Parallel
import cats.effect.Async
import cats.effect.std.Console
import com.theproductcollectiveco.play4s.Play4sApi
import com.theproductcollectiveco.play4s.game.sudoku.{SudokuComputationSummary, Algorithm, GameId, BoardState}
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, Orchestrator, Search}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{TraceClient, GoogleCloudVisionClient}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import smithy4s.Timestamp
import scala.concurrent.duration.DurationLong

trait Play4sService[F[_]] extends Play4sApi[F] {}

object Play4sService {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console: Parallel]: Play4sService[F] =
    new Play4sService[F] with Play4sApi[F]:
      override def computeSudoku(image: smithy4s.Blob): F[SudokuComputationSummary] =

        for {
          start                <- Clock[F].monotonic
          requestedAt          <-
            Clock[F].realTime.map { t =>
              val seconds = t.toSeconds
              val nanos   = (t - seconds.seconds).toNanos
              Timestamp(seconds, nanos.toInt)
            }
          gameId               <- UUIDGen[F].randomUUID.map(uuid => GameId(uuid.toString))
          given Logger[F]       = Slf4jLogger.getLogger[F]
          given Metrics[F]      = Metrics[F]
          imageParser           = GoogleCloudVisionClient[F]
          traceParser           = TraceClient[F]
          orchestrator          = Orchestrator[F](traceParser, imageParser)
          backtrackingAlgorithm = BacktrackingAlgorithm[F]()
          parsedLines          <- orchestrator.processImage(image.toArray).map(_ :: Nil)
          headOption           <-
            parsedLines
              .parTraverse { trace =>
                for {
                  gameBoard <- orchestrator.createBoard(orchestrator.processLine(trace))
                  solutions <-
                    orchestrator.solve(
                      gameBoard,
                      Search(),
                      backtrackingAlgorithm,
                    )
                  _         <- gameBoard.delete()
                } yield solutions
              }
              // overriding support for multiple image uploads within one request at this time ..
              .map:
                _.head
          end                  <- Clock[F].monotonic
          duration              = (end - start).toMillis
        } yield SudokuComputationSummary(
          id = gameId,
          strategy = Algorithm(backtrackingAlgorithm.getClass().getName()),
          duration = duration,
          requestedAt = requestedAt,
          solution = headOption.map(_.value).map(BoardState.apply),
        )

}
