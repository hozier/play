package com.theproductcollectiveco.play4s.api

import com.theproductcollectiveco.play4s.internal.meta.health.{ServiceMetaApi, CheckHealthOutput}
import com.theproductcollectiveco.play4s.internal.meta.health.HealthStatus.HEALTHY
import cats.implicits.*
import cats.effect.Async

object HealthService {
  def apply[F[_]: Async]: ServiceMetaApi[F] = new ServiceMetaApi[F] {
    override def checkHealth(): F[CheckHealthOutput] = CheckHealthOutput(HEALTHY).pure
     
  }
}
