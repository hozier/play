package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.Parallel
import cats.data.NonEmptyList
import cats.effect.{Async, MonadCancelThrow}
import cats.effect.kernel.Ref
import cats.effect.std.Console
import cats.effect.syntax.spawn.*
import cats.implicits.*
import com.theproductcollectiveco.play4s.game.sudoku.{BoardState, InitialStateSettingError}
import com.theproductcollectiveco.play4s.game.sudoku.parser.*
import com.theproductcollectiveco.play4s.store.Board
import fs2.io.file.Files
import org.typelevel.log4cats.Logger

trait Orchestrator[F[_]] {
  def fetchResourceBytes(fileName: String): F[Array[Byte]]
  def processImage(image: Array[Byte]): F[String]
  def processTrace(fileName: String): F[List[String]]
  def processLine(line: String): F[BoardState]
  def createBoard(state: BoardState): F[Board[F]]
  def solve(board: Board[F], search: Search, algorithms: Algorithm[F]*): F[Option[SolvedState]]
}

object Orchestrator {

  def make[F[_]: MonadCancelThrow: Async: Logger: Console: Parallel: Files](
    traceParser: TraceParser[F],
    imageParser: ImageParser[F],
  ): Orchestrator[F] =
    new Orchestrator[F]() {
      override def createBoard(state: BoardState): F[Board[F]] =
        for {
          ref   <- Ref.of[F, Option[BoardState]](None)
          board <- Logger[F].debug("Creating board") *> Board(state, ref)
        } yield board

      override def processImage(image: Array[Byte]): F[String] = imageParser.parseImage(image)

      override def processTrace(fileName: String): F[List[String]] = traceParser.parseResource(fileName)

      override def processLine(line: String): F[BoardState] = traceParser.parseLine(line)

      override def fetchResourceBytes(fileName: String): F[Array[Byte]] = imageParser.fetchResourceBytes(fileName)

      override def solve(
        board: Board[F],
        search: Search,
        algorithms: Algorithm[F]*
      ): F[Option[SolvedState]] =
        NonEmptyList.fromList(algorithms.toList) match {
          case None                     => InitialStateSettingError("No algorithms provided").raiseError
          case Some(nonEmptyAlgorithms) =>
            for {
              _        <- Logger[F].debug("Solving board")
              solution <-
                nonEmptyAlgorithms
                  .map(_.solve(board, search))
                  .reduceLeft:
                    /** Race all algorithms concurrently and merge Either to Option */
                    _.race(_)
                      .map(_.merge)
              _        <- Logger[F].debug("Board solved")
            } yield solution
        }
    }

}
