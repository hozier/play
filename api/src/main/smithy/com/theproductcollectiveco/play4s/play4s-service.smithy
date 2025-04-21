$version: "2"

namespace com.theproductcollectiveco.play4s

use alloy#simpleRestJson
use com.theproductcollectiveco.play4s.game.sudoku#InvalidInputError
use com.theproductcollectiveco.play4s.game.sudoku#NoSolutionFoundError
use com.theproductcollectiveco.play4s.game.sudoku#BoardNotCreatedError
use com.theproductcollectiveco.play4s.game.sudoku#InitialStateSettingError
use com.theproductcollectiveco.play4s.game.sudoku#InternalServerError
use com.theproductcollectiveco.play4s.game.sudoku#DecodeFailureError
use com.theproductcollectiveco.play4s.game.sudoku.public#ComputeSudoku
use com.theproductcollectiveco.play4s.game.sudoku.internal#ComputeSudokuDeveloperMode
use com.theproductcollectiveco.play4s.game.sudoku.internal#GetSudokuHints
use com.theproductcollectiveco.play4s.game.sudoku.internal#GetSudokuMetrics

@simpleRestJson
service Play4sApi {
    version: "v1"
    operations: [ComputeSudoku, ComputeSudokuDeveloperMode, GetSudokuHints, GetSudokuMetrics]
    errors: [InvalidInputError, NoSolutionFoundError, BoardNotCreatedError, InitialStateSettingError, DecodeFailureError, InternalServerError]
}