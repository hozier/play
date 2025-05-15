$version: "2"

namespace com.theproductcollectiveco.play4s.internal.auth

@error("client")
@httpError(401)
structure AuthError {
    @required
    description: String
}

@error("client")
@httpError(429)
structure AuthEligibilityError  {
    @required
    description: String
}

@error("server")
@httpError(500)
structure DecodeFailureError  {
    @required
    description: String
}

@error("server")
@httpError(500)
structure AuthProcessingError  {
    @required
    description: String
}