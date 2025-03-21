$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.api

@error("client")
@httpError(400)
structure InvalidInputError {
    message: String
}

@error("client")
@httpError(404)
structure NoSolutionFoundError {
    message: String
}

@error("server")
@httpError(500)
structure BoardNotCreatedError {
    message: String
}

@error("server")
@httpError(500)
structure InitialStateSettingError {
    message: String
}

@error("server")
@httpError(500)
structure InternalServerError {
    message: String
}