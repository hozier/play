$version: "2"

namespace com.theproductcollectiveco.play4s

use com.theproductcollectiveco.play4s.game.sudoku#InvalidInputError
use com.theproductcollectiveco.play4s.game.sudoku#NoSolutionFoundError
use com.theproductcollectiveco.play4s.game.sudoku#BoardNotCreatedError
use com.theproductcollectiveco.play4s.game.sudoku#InitialStateSettingError
use com.theproductcollectiveco.play4s.game.sudoku#InternalServerError
use com.theproductcollectiveco.play4s.game.sudoku.public#ComputeSudoku

service Play4sApi {
    version: "v1"
    operations: [ComputeSudoku]
    errors: [InvalidInputError, NoSolutionFoundError, BoardNotCreatedError, InitialStateSettingError, InternalServerError]
}