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

structure RuntimeConfig {
    @required
    appName: String

    @required
    appVersion: String

    @required
    scalaVersion: String

    @required
    sbtVersion: String

    @required
    organization: String

    @required
    artifactIdentifiers: ArtifactIdentifiers

    onCI: Boolean

    withJwt: Boolean
}

structure ArtifactIdentifiers {
    @required
    gitSha: String

    @required
    imageDigest: String

    @required
    @timestampFormat("date-time")
    builtAt: Timestamp
}