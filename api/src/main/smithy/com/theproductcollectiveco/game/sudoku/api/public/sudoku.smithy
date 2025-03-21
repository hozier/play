$version: "2"

namespace com.theproductcollectiveco.game.sudoku.api.public

use com.theproductcollectiveco.game.sudoku.api#InvalidInputError
use com.theproductcollectiveco.game.sudoku.api#NoSolutionFoundError
use com.theproductcollectiveco.game.sudoku.api#BoardNotCreatedError
use com.theproductcollectiveco.game.sudoku.api#InitialStateSettingError
use com.theproductcollectiveco.game.sudoku.api#InternalServerError
use com.theproductcollectiveco.game.sudoku.api#ComputeRequest
use com.theproductcollectiveco.game.sudoku.api#GameMetadata
use com.theproductcollectiveco.game.sudoku.api#SudokuSolution


service Play4sService {
    version: "v1"
    operations: [ComputeSudoku]
    errors: [InvalidInputError, NoSolutionFoundError, BoardNotCreatedError, InitialStateSettingError, InternalServerError]
}

@http(method: "POST", uri: "/game/sudoku/solve", code: 200)
operation ComputeSudoku {
    input := with [ComputeRequest] {} 
    output := with [GameMetadata] {
        solution: SudokuSolution
    }
}