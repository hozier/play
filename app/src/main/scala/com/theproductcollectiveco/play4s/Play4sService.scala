package com.theproductcollectiveco.play4s

import cats.implicits.*
import cats.syntax.all.*
import cats.Parallel
import cats.effect.Async
import cats.effect.std.Console
import com.theproductcollectiveco.play4s.Play4sApi
import com.theproductcollectiveco.play4s.game.sudoku.public.ComputeSudokuOutput
import com.theproductcollectiveco.play4s.game.sudoku.core.{BacktrackingAlgorithm, Orchestrator, Search}
import com.theproductcollectiveco.play4s.game.sudoku.parser.{TraceClient, GoogleCloudVisionClient}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger

trait Play4sService[F[_]] extends Play4sApi[F] {}

object Play4sService {

  def apply[F[_]: Async: Console: Parallel]: Play4sService[F] = new Play4sService[F] with Play4sApi[F] {
    override def computeSudoku(image: smithy4s.Blob): F[ComputeSudokuOutput] = {
      
      given Logger[F]  = Slf4jLogger.getLogger[F]
      given Metrics[F] = Metrics[F]

      for {
        traceParser <- TraceClient[F].pure[F]
        imageParser <- GoogleCloudVisionClient[F].pure[F]
        orchestrator <- Orchestrator[F](traceParser, imageParser).pure[F]
        // Add your logic here to use the orchestrator and compute the Sudoku
        parsedLines <- orchestrator.processImage(image.toArray).map(_ :: Nil) 
        _ <- parsedLines.parTraverse { line => 
          for {
            gameBoard <- orchestrator.createBoard(orchestrator.processLine(line))
            solutions <- orchestrator.solve( // refactor to return board
                  gameBoard,
                  Search(),
                  BacktrackingAlgorithm[F](),
                )
              _  <- gameBoard.delete()
            } yield solutions
        } yield ComputeSudokuOutput(id = ???, strategy = ???, duration = ???, requestedAt = ???, solution = result)
      }
    }
  }
}
