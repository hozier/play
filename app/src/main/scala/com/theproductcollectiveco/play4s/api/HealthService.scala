package com.theproductcollectiveco.play4s.api

import com.theproductcollectiveco.play4s.internal.meta.health.{ServiceMetaApi, CheckHealthOutput, RuntimeConfig}
import com.theproductcollectiveco.play4s.internal.meta.health.HealthStatus.HEALTHY
import cats.implicits.*
import cats.effect.Async
import com.theproductcollectiveco.play4s.config.AppConfig

object HealthService {

  def apply[F[_]: Async](appConfig: AppConfig): ServiceMetaApi[F] =
    new ServiceMetaApi[F] {

      override def getVersion(): F[RuntimeConfig] = appConfig.runtime.pure

      override def checkHealth(): F[CheckHealthOutput] = CheckHealthOutput(HEALTHY).pure

    }

}
