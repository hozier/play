package com.theproductcollectiveco.play4s

import cats.effect.{Clock, Ref, Sync}
import cats.implicits.*
import io.circe.Json
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

trait Metrics[F[_]] {
  def time[A](name: String)(fa: F[A]): F[A]
  def incrementCounter(name: String): F[Unit]
  def getCounter(name: String): F[Long]
  def updateGauge(name: String, value: Double): F[Unit]
  def getGauge(name: String): F[Double]
  def recordHistogram(name: String, value: String): F[Unit]
  def getHistogram(name: String): F[Map[String, Long]]
  def debugMetrics: F[Unit]
}

object Metrics {

  def apply[F[_]](using metrics: Metrics[F]): Metrics[F] = metrics

  def make[F[_]: Sync: Clock: Logger]: F[Metrics[F]] =
    for {
      counters   <- Ref.of[F, Map[String, Long]](Map.empty.withDefaultValue(0L))
      gauges     <- Ref.of[F, Map[String, Double]](Map.empty.withDefaultValue(-1.0))
      histograms <- Ref.of[F, Map[String, Map[String, Long]]](Map.empty.withDefaultValue(Map.empty.withDefaultValue(0L)))
    } yield new Metrics[F] {

      override def time[A](name: String)(fa: F[A]): F[A] =
        for {
          start   <- Clock[F].monotonic
          result  <- fa
          end     <- Clock[F].monotonic
          duration = (end - start).toMillis
          _       <- Logger[F].debug(s"$name took $duration ms")
        } yield result

      override def incrementCounter(name: String): F[Unit] =
        counters.update(map => map.updated(name, map(name) + 1)) *> Logger[F].debug(s"Counter incremented: $name")

      override def getCounter(name: String): F[Long] = counters.get.map(_(name))

      override def updateGauge(name: String, value: Double): F[Unit] =
        gauges.update(map => map.updated(name, value)) *> Logger[F].debug(s"Gauge updated: $name -> $value")

      override def getGauge(name: String): F[Double] = gauges.get.map(_(name))

      override def recordHistogram(name: String, value: String): F[Unit] =
        histograms.update { map =>
          val histogram = map(name)
          map.updated(name, histogram.updated(value, histogram(value) + 1))
        } *> Logger[F].debug(s"Histogram updated: $name")

      override def getHistogram(name: String): F[Map[String, Long]] = histograms.get.map(_(name))

      override def debugMetrics: F[Unit] =
        for {
          countersSnapshot   <- counters.get
          gaugesSnapshot     <- gauges.get
          histogramsSnapshot <- histograms.get
          jsonLog             =
            Map(
              "counters"   -> countersSnapshot.asJson,
              "gauges"     -> gaugesSnapshot.asJson,
              "histograms" -> histogramsSnapshot.asJson,
            ).asJson.noSpaces
          _                  <- Logger[F].debug(s"Metrics Snapshot: $jsonLog")
        } yield ()
    }

}

object NoopMetrics {

  def apply[F[_]: Sync]: Metrics[F] =
    new Metrics[F] {
      def time[A](name: String)(fa: F[A]): F[A]                 = fa
      def incrementCounter(name: String): F[Unit]               = Sync[F].unit
      def getCounter(name: String): F[Long]                     = Sync[F].pure(0L)
      def updateGauge(name: String, value: Double): F[Unit]     = Sync[F].unit
      def getGauge(name: String): F[Double]                     = Sync[F].pure(-1.0)
      def recordHistogram(name: String, value: String): F[Unit] = Sync[F].unit
      def getHistogram(name: String): F[Map[String, Long]]      = Sync[F].pure(Map.empty)
      def debugMetrics: F[Unit]                                 = Sync[F].unit
    }

}
