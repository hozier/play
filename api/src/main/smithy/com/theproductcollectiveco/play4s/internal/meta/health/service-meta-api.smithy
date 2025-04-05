$version: "2"

namespace com.theproductcollectiveco.play4s.internal.meta.health

use alloy#simpleRestJson
use com.theproductcollectiveco.play4s.internal.meta.health#CheckHealthOutput
use com.theproductcollectiveco.play4s.internal.meta.health#GetVersionOutput

@simpleRestJson
service ServiceMetaApi {
    operations: [
        CheckHealth
    ]
}

@readonly
@http(method: "GET", uri: "/internal/meta/health")
operation CheckHealth {
    output: CheckHealthOutput
}

