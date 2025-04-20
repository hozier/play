package com.theproductcollectiveco.play4s.store

import cats.effect.{Async, MonadCancelThrow}
import cats.implicits.*
import cats.data.OptionT
import cats.effect.kernel.Ref
import cats.effect.std.Console
import org.typelevel.log4cats.Logger
import com.theproductcollectiveco.play4s.Metrics
import com.theproductcollectiveco.play4s.game.sudoku.{InitialStateSettingError, BoardNotCreatedError, BoardState}

trait Board[F[_]] {
  def read(): F[BoardState]
  def update(externalState: BoardState): F[Unit]
  def delete(): F[Unit]
}

object Board {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console: Metrics](
    initialState: BoardState,
    store: Ref[F, Option[BoardState]],
  ): F[Board[F]] =
    store
      .set(Some(initialState))
      .handleErrorWith { case e => Logger[F].error(e)("Error setting initial state") *> InitialStateSettingError(e.getMessage()).raiseError }
      .as:
        new Board[F] {
          override def read(): F[BoardState] =
            Metrics[F].time("Board.read") {
              Logger[F].debug("Getting board state") *> OptionT(store.get)
                .getOrElseF(BoardNotCreatedError("Board not created").raiseError[F, BoardState])
            }

          override def update(externalState: BoardState): F[Unit] =
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

  def prettyPrintBoard(board: BoardState): String =
    board.value
      .map:
        _.map {
          case 0 => "."
          case n => n.toString
        }
          .mkString(" ")
      .mkString("\n")

}
