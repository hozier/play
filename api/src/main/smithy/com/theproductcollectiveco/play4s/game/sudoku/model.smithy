$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku

use smithy4s.meta#vector
use com.theproductcollectiveco.play4s.game.sudoku.public#ComputeSudoku

@pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
@documentation("Game ID; should be a UUID")
string GameId

// A vector of integers.
@vector
list VectorList {
    member: Integer
}

// A vector whose members are VectorList.
@vector
list NestedVectorList {
    member: VectorList
}

// A list of algorithms
list StrategyList {
    member: Strategy
}

// A structure that contains a nested vector of integers.
structure BoardState {
    @required
    value: NestedVectorList
}

@mixin
structure ComputeRequest {
    @required
    image: Blob
}

@mixin
structure ComputeRequestDeveloperMode {
    @required
    trace: String
}

// Enum to represent the Strategy types with custom string values.
enum Strategy {
    @documentation("Backtracking strategy for solving Sudoku.")
    @enumValue("backtracking")
    BACKTRACKING

    @documentation("Constraint propagation strategy for solving Sudoku.")
    @enumValue("constraintPropagation")
    CONSTRAINT_PROPAGATION
}

@mixin
structure GameMetadata {
    @required
    id: GameId 
    @required
    duration: Long
    @required
    @timestampFormat("date-time")
    @documentation("UTC timestamp at the time the image was uploaded")
    @required
    requestedAt: Timestamp
    @required
    strategies: StrategyList
    fastestOutcome: Strategy
}

structure SudokuComputationSummary with [GameMetadata] {
    solution: BoardState
}