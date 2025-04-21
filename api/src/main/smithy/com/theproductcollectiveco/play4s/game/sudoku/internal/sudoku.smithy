$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.internal

use com.theproductcollectiveco.play4s.game.sudoku#ComputeRequestDeveloperMode
use com.theproductcollectiveco.play4s.game.sudoku#SudokuComputationSummary
use com.theproductcollectiveco.play4s.game.sudoku#GetHintsRequest
use com.theproductcollectiveco.play4s.game.sudoku#EmptyCellHints
use com.theproductcollectiveco.play4s.game.sudoku#GameMetrics

@documentation("Internal endpoint used for developer debugging that computes solutions without image processing. It accepts a trace string representing a serialized board state.")
@http(method: "POST", uri: "/internal/game/sudoku/solve", code: 200)
operation ComputeSudokuDeveloperMode {
    input := with [ComputeRequestDeveloperMode] {} 
    output: SudokuComputationSummary
} 

@documentation("Internal endpoint allowing users to request hints for a Sudoku puzzle. Users can specify the number of hints they want.")
@http(method: "POST", uri: "/internal/game/sudoku/hints", code: 200)
operation GetSudokuHints {
    input := with [GetHintsRequest] {} 
    output: EmptyCellHints
}

@documentation("Internal endpoint to retrieve Sudoku computation statistics, such as total puzzles solved, average solve time, and algorithm usage.")
@http(method: "GET", uri: "/internal/game/sudoku/metrics", code: 200)
operation GetSudokuMetrics {
    output: GameMetrics
}