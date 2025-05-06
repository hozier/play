$version: "2"

namespace com.theproductcollectiveco.play4s.internal.auth

use com.theproductcollectiveco.play4s.internal.auth#Token
use com.theproductcollectiveco.play4s.internal.auth#GenericHandle
use alloy#simpleRestJson


@simpleRestJson
service ServiceAuthApi {
    operations: [
        RequestToken
    ]
}

@readonly
@http(method: "POST", uri: "/internal/auth/token")
operation RequestToken {
    input := {
        @required
        requester: GenericHandle
    }
    output: Token
}
