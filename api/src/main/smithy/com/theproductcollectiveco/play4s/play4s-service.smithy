$version: "2"

namespace com.theproductcollectiveco.play4s

use alloy#simpleRestJson
use smithy.api#httpApiKeyAuth
use smithy.api#auth

use com.theproductcollectiveco.play4s.internal.auth#AuthError
use com.theproductcollectiveco.play4s.game.sudoku#ForbiddenError
use com.theproductcollectiveco.play4s.game.sudoku#InvalidInputError
use com.theproductcollectiveco.play4s.game.sudoku#NoSolutionFoundError
use com.theproductcollectiveco.play4s.game.sudoku#BoardNotCreatedError
use com.theproductcollectiveco.play4s.game.sudoku#InitialStateSettingError
use com.theproductcollectiveco.play4s.game.sudoku#InternalServerError
use com.theproductcollectiveco.play4s.internal.auth#DecodeFailureError
use com.theproductcollectiveco.play4s.game.sudoku.public#ComputeSudoku
use com.theproductcollectiveco.play4s.game.sudoku.internal#ComputeSudokuDeveloperMode
use com.theproductcollectiveco.play4s.game.sudoku.internal#GetSudokuHints
use com.theproductcollectiveco.play4s.game.sudoku.internal#GetSudokuMetrics

@simpleRestJson
@auth([httpApiKeyAuth])
@httpApiKeyAuth(
    scheme: "Bearer",
    name: "httpBearerAuth",
    in: "header",
    keyName: "Authorization"
)
service Play4sApi {
    version: "2025-04-23",
    operations: [ComputeSudoku, ComputeSudokuDeveloperMode, GetSudokuHints, GetSudokuMetrics],
    errors: [AuthError, ForbiddenError, InvalidInputError, NoSolutionFoundError, BoardNotCreatedError, InitialStateSettingError, DecodeFailureError, InternalServerError]
}