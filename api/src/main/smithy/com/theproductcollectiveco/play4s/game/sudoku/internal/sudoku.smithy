$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.internal

use com.theproductcollectiveco.play4s.game.sudoku#ComputeRequestDeveloperMode
use com.theproductcollectiveco.play4s.game.sudoku#SudokuComputationSummary

@documentation("Internal endpoint used for developer debugging that computes solutions without image processing. It accepts a trace string representing a serialized board state.")
@http(method: "POST", uri: "/internal/game/sudoku/solve", code: 200)
operation ComputeSudokuDeveloperMode {
    input := with [ComputeRequestDeveloperMode] {} 
    output: SudokuComputationSummary
} 
