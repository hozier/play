$version: "2"

namespace com.theproductcollectiveco.play4s

use com.theproductcollectiveco.play4s.game.sudoku.api#InvalidInputError
use com.theproductcollectiveco.play4s.game.sudoku.api#NoSolutionFoundError
use com.theproductcollectiveco.play4s.game.sudoku.api#BoardNotCreatedError
use com.theproductcollectiveco.play4s.game.sudoku.api#InitialStateSettingError
use com.theproductcollectiveco.play4s.game.sudoku.api#InternalServerError
use com.theproductcollectiveco.play4s.game.sudoku.api.public#ComputeSudoku

service Play4sService {
    version: "v1"
    operations: [ComputeSudoku]
    errors: [InvalidInputError, NoSolutionFoundError, BoardNotCreatedError, InitialStateSettingError, InternalServerError]
}