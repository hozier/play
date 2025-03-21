package com.theproductcollectiveco.play4s.store

import cats.effect.{Async, MonadCancelThrow}
import cats.effect.std.Console
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.Metrics
import cats.implicits.*
import cats.effect.kernel.Ref

trait Board[F[_]] {
  def read(): F[Board.BoardData]
  def update(externalState: Board.BoardData): F[Unit]
  def delete(): F[Unit]
}

object Board {
  type BoardData = Vector[Vector[Int]]

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console: Metrics](
    initialState: BoardData,
    store: Ref[F, Option[BoardData]],
  ): F[Board[F]] =
    store
      .set(Some(initialState))
      .handleErrorWith { case e =>
        Logger[F].error(e)("Error setting initial state") *> e
          .raiseError[F, Unit]
      }
      .as:
        new Board[F] {
          override def read(): F[BoardData] =
            Metrics[F].time("Board.read") {
              Logger[F].debug("Getting board state") *> store.get.flatMap {
                case Some(board) =>
                  board
                    .pure[F]
                case None        =>
                  new RuntimeException("Board not created")
                    .raiseError[F, BoardData]
              }
            }

          override def update(externalState: BoardData): F[Unit] =
            Metrics[F].time("Board.update") {
              for {
                _ <-
                  read().flatMap: board =>
                    Logger[F].info:
                      s"\ncurrent:\n${prettyPrintBoard(board)}\nnext:\n${prettyPrintBoard(externalState)}"
                _ <- Logger[F].debug("Updating board state")
                _ <- store.set(externalState.some)
              } yield ()
            }

          override def delete(): F[Unit] =
            Metrics[F].time("Board.delete") {
              Logger[F].debug("Deleting board state") *> store.set(None)
            }
        }

  def prettyPrintBoard(board: BoardData): String = board.map(row => s"${row.mkString(" ")}").mkString("\n")
}
