$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku

@error("client")
@httpError(400)
structure InvalidInputError {
    @required
    description: String
}

@error("client")
@httpError(403)
structure ForbiddenError {
    description: String
}

@error("client")
@httpError(422)
structure NoSolutionFoundError {
    @required
    description: String
}

@error("server")
@httpError(500)
structure BoardNotCreatedError {
    @required
    description: String
}

@error("server")
@httpError(500)
structure InitialStateSettingError {
    @required
    description: String
}

@error("server")
@httpError(500)
structure InternalServerError {
    @required
    description: String
}