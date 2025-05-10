$version: "2"

namespace com.theproductcollectiveco.play4s.internal.auth

use com.theproductcollectiveco.play4s.internal.auth#Otp
use com.theproductcollectiveco.play4s.internal.auth#Token
use com.theproductcollectiveco.play4s.internal.auth#GenericHandle
use com.theproductcollectiveco.play4s.game.sudoku#AuthError
use com.theproductcollectiveco.play4s.game.sudoku#AuthValidationError
use com.theproductcollectiveco.play4s.game.sudoku#AuthProcessingError
use com.theproductcollectiveco.play4s.game.sudoku#InvalidInputError

use alloy#simpleRestJson


@simpleRestJson
service ServiceAuthApi {
    operations: [
        RequestToken,
        InitiateOtpRequest,
        RedeemOtp
    ],
    errors: [AuthError, AuthValidationError, AuthProcessingError, InvalidInputError]
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

@readonly
@http(method: "POST", uri: "/internal/auth/otp/initiate", code: 201)
operation InitiateOtpRequest {
    input := {
        @required
        requester: GenericHandle
    }
}

@readonly
@http(method: "POST", uri: "/internal/auth/otp/authorize")
operation RedeemOtp {
    input := {
        @required
        requester: GenericHandle
        @required
        otp: Otp
    }
    output: Token
}
