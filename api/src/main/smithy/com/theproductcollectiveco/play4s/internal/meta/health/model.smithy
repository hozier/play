$version: "2"

namespace com.theproductcollectiveco.play4s.internal.meta.health

structure CheckHealthOutput {
    @required
    status: HealthStatus
}

enum HealthStatus {
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"
}

structure GetVersionOutput {
    @required
    name: String

    @required
    @timestampFormat("date-time")
    builtAt: Timestamp

    @required
    version: String

    @required
    scalaVersion: String

    sbtVersion: String
}