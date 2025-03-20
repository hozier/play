package com.theproductcollectiveco

import cats.effect.Clock
import cats.implicits.*
import cats.effect.Sync
import org.typelevel.log4cats.Logger

trait Metrics[F[_]] {
  def time[A](name: String)(fa: F[A]): F[A]
}

object Metrics {

  def apply[F[_]: Sync: Clock: Logger]: Metrics[F] =
    new Metrics[F] {
      override def time[A](name: String)(fa: F[A]): F[A] =
        for {
          start   <- Clock[F].monotonic
          result  <- fa
          end     <- Clock[F].monotonic
          duration = (end - start).toMillis
          _       <- Logger[F].info(s"$name took $duration ms")
        } yield result
    }

}
