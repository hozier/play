package com.theproductcollectiveco.play4s.game.sudoku.core

import cats.effect.{Async, MonadCancelThrow}
import cats.effect.kernel.Ref
import org.typelevel.log4cats.Logger
import cats.effect.std.Console
import cats.implicits.*
import cats.effect.syntax.spawn.*
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.store.Board
import com.theproductcollectiveco.play4s.game.sudoku.parser.*
import cats.Parallel
import cats.data.NonEmptyList
import fs2.io.file.Files
import com.theproductcollectiveco.play4s.game.sudoku.BoardState

trait Orchestrator[F[_]] {
  def fetchBytes(fileName: String): F[Array[Byte]]
  def processImage(image: Array[Byte]): F[String]
  def processTrace(fileName: String): F[List[String]]
  def processLine(line: String): BoardState
  def createBoard(state: BoardState): F[Board[F]]
  def solve(board: Board[F], search: Search, algorithms: Algorithm[F]*): F[Option[SolvedState]]
}

object Orchestrator {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console: Parallel: Files: Metrics](
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

      override def processLine(line: String): BoardState = traceParser.parseLine(line)

      override def fetchBytes(fileName: String): F[Array[Byte]] = imageParser.fetchBytes(fileName)

      override def solve(
        board: Board[F],
        search: Search,
        algorithms: Algorithm[F]*
      ): F[Option[SolvedState]] =
        NonEmptyList.fromList(algorithms.toList) match {
          case None                     => Async[F].raiseError(new RuntimeException("No algorithms provided"))
          case Some(nonEmptyAlgorithms) =>
            nonEmptyAlgorithms
              .map(_.solve(board, search))
              .foldLeft(Async[F].pure(Option.empty[SolvedState])) { (acc, next) =>
                acc
                  .race(next)
                  .flatMap:
                    case Left(Some(result))  => result.some.pure
                    case Right(Some(result)) => result.some.pure
                    case Left(None)          => next
                    case Right(None)         => acc
              }
        }
    }

}
