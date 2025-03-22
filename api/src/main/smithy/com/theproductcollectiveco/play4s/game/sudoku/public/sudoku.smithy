$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.public

use com.theproductcollectiveco.play4s.game.sudoku#ComputeRequest
use com.theproductcollectiveco.play4s.game.sudoku#GameMetadata
use com.theproductcollectiveco.play4s.game.sudoku#SudokuSolution

@http(method: "POST", uri: "/game/sudoku/solve", code: 200)
operation ComputeSudoku {
    input := with [ComputeRequest] {} 
    output := with [GameMetadata] {
        solution: SudokuSolution
    }
}