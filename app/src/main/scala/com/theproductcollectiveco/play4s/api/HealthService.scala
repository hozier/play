package com.theproductcollectiveco.play4s.api

import cats.effect.Async
import cats.implicits.*
import com.theproductcollectiveco.play4s.config.AppConfig
import com.theproductcollectiveco.play4s.internal.meta.health.{CheckHealthOutput, RuntimeConfig, ServiceMetaApi}
import com.theproductcollectiveco.play4s.internal.meta.health.HealthStatus.HEALTHY

object HealthService {

  def make[F[_]: Async](using appConfig: AppConfig): ServiceMetaApi[F] =
    new ServiceMetaApi[F] {

      override def getVersion(): F[RuntimeConfig] = appConfig.runtime.pure

      override def checkHealth(): F[CheckHealthOutput] = CheckHealthOutput(HEALTHY).pure

    }

}
