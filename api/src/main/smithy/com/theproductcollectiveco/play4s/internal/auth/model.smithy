$version: "2"

namespace com.theproductcollectiveco.play4s.internal.auth

structure Alias {
    @required
    value: String
}

structure Token {
    @required
    value: String
}

structure Grant {
    @required
    magicLink: MagicLink
}

structure MagicLink {
    @required
    payload: Payload
    @required
    oneTimeUse: Boolean
    @required
    issuer: String
}

structure Payload {
    @required
    genericHandle: GenericHandle
    @required
    expiration: Long
    @required
    issuedAt: Long
    @required
    roles: RolesList
    @pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    @required
    tokenId: String
    metadata: MetadataList
}

union GenericHandle {
    contact: Contact
    username: Username
}

structure EmailAddress {
    @pattern("^(?=.{1,320}$)(?=.{1,64}@.{1,255}$)([A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+)$")
    @required
    value: String
}

structure Username {
    @required
    value: String
}

structure PhoneNumber {
    @pattern("^\\+?[0-9]{7,15}$")
    @required
    value: String
}

structure OtpSession {
    @required
    otp: Otp
    otpContext: OtpContext
    @required
    expiresAt: Timestamp
    @default(0)
    @required
    initiateAttempts: Integer
    @default(0)
    @required
    validateAttempts: Integer
    @default(false)
    @required
    isRedeemed: Boolean
    @required
    handle: GenericHandle
}


structure Otp {
    @required
    @pattern("^[A-Za-z0-9]{6}$")
    value: String
}

structure OtpContext {
    @required
    value: String
}

union Contact {
    emailAddress: EmailAddress
    phoneNumber: PhoneNumber
}

list RolesList {
    member: String
}

list MetadataList {
    member: Metadata
}

structure Metadata {
    @required
    key: String

    @required
    value: String
}