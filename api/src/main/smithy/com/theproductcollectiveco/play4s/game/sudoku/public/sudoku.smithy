$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.public

use com.theproductcollectiveco.play4s.game.sudoku#ComputeRequest
use com.theproductcollectiveco.play4s.game.sudoku#SudokuComputationSummary

@http(method: "POST", uri: "/public/game/sudoku/solve", code: 200)
operation ComputeSudoku {
    input := with [ComputeRequest] {} 
    output: SudokuComputationSummary
} 
