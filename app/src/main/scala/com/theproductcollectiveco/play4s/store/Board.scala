package com.theproductcollectiveco.play4s.store

import cats.effect.{Async, MonadCancelThrow}
import cats.effect.kernel.Ref
import cats.effect.std.Console
import cats.implicits.*
import com.theproductcollectiveco.play4s.game.sudoku.{BoardNotCreatedError, BoardState, InitialStateSettingError}
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

trait Board[F[_]] {
  def read(): F[BoardState]
  def update(externalState: BoardState): F[Unit]
  def delete(): F[Unit]
}

object Board {

  def apply[F[_]: MonadCancelThrow: Async: Logger: Console](
    initialState: BoardState,
    store: Ref[F, Option[BoardState]],
  ): F[Board[F]] =
    store
      .set(Some(initialState))
      .handleErrorWith { case e => Logger[F].error(e)("Error setting initial state") *> InitialStateSettingError(e.getMessage()).raiseError }
      .as:
        new Board[F] {
          override def read(): F[BoardState] =
            Logger[F].debug("Getting board state") *>
              store.get.flatMap(_.liftTo[F](BoardNotCreatedError("Board not created")))

          override def update(externalState: BoardState): F[Unit] =
            for {
              _ <-
                read().flatMap { board =>
                  val jsonLog =
                    Map(
                      "current" -> board.value.map(_.toList).toList.asJson,
                      "next"    -> externalState.value.map(_.toList).toList.asJson,
                    ).asJson.noSpaces
                  Logger[F].debug(s"Board State Snapshot: $jsonLog")
                }
              _ <- Logger[F].debug("Updating board state")
              _ <- store.set(externalState.some)
            } yield ()

          override def delete(): F[Unit] = Logger[F].debug("Deleting board state") *> store.set(None)
        }

}
