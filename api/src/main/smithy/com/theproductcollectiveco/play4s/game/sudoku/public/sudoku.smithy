$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku.public

use com.theproductcollectiveco.play4s.game.sudoku#ComputeRequest
use com.theproductcollectiveco.play4s.game.sudoku#SudokuComputationSummary

@documentation("Public endpoint allowing users to submit Sudoku puzzles as images and receive a solution if solvable. Supports requests sent as either multipart/form-data (deprecated) or application/json. When using application/json, the image must be provided as a base64-encoded string.")
@http(method: "POST", uri: "/public/game/sudoku/solve", code: 200)
operation ComputeSudoku {
    input := with [ComputeRequest] {} 
    output: SudokuComputationSummary
} 
