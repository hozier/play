$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.internal

use com.theproductcollectiveco.play4s.game.sudoku#ComputeRequestDeveloperMode
use com.theproductcollectiveco.play4s.game.sudoku#SudokuComputationSummary

@http(method: "POST", uri: "/internal/game/sudoku/solve", code: 200)
operation ComputeSudokuDeveloperMode {
    input := with [ComputeRequestDeveloperMode] {} 
    output: SudokuComputationSummary
} 
