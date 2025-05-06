$version: "2"

namespace com.theproductcollectiveco.play4s.internal.auth

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
    @required
    tokenId: String
    metadata: MetadataList
}

union GenericHandle {
    emailAddress: EmailAddress
    username: Username
}

structure EmailAddress {
    @required
    value: String
}

structure Username {
    @required
    value: String
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