$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.api.public

use com.theproductcollectiveco.play4s.game.sudoku.api#ComputeRequest
use com.theproductcollectiveco.play4s.game.sudoku.api#GameMetadata
use com.theproductcollectiveco.play4s.game.sudoku.api#SudokuSolution

@http(method: "POST", uri: "/game/sudoku/solve", code: 200)
operation ComputeSudoku {
    input := with [ComputeRequest] {} 
    output := with [GameMetadata] {
        solution: SudokuSolution
    }
}