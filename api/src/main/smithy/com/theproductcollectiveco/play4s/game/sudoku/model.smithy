$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku

use smithy4s.meta#vector
use com.theproductcollectiveco.play4s.game.sudoku.public#ComputeSudoku

@pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
@documentation("Game ID; should be a UUID")
string GameId

// A vector of integers.
@vector
list VectorIntegerList {
    member: Integer
}

// A vector whose members are VectorIntegerList.
@vector
list NestedVectorList {
    member: VectorIntegerList
}

// A list of algorithms
list StrategyList {
    member: Strategy
}

// A list whose members are VectorIntegerList.
list CellHintList {
    member: CellHint
}

list IntegerList {
    member: Integer
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

// A structure to represent details of concurrent execution.
structure ConcurrentExecutionDetails {
    @required
    strategies: StrategyList
    @documentation("The strategy that completed earliest in the concurrent execution.")
    earliestCompleted: Strategy
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
    @documentation("Details about the concurrent execution of strategies.")
    @required
    concurrentExecutionDetails: ConcurrentExecutionDetails
}

structure SudokuComputationSummary with [GameMetadata] {
    solution: BoardState
}

structure EmptyCell {
    @documentation("The row index of the cell.")
    @required
    row: Integer
    @documentation("The column index of the cell.")
    @required
    col: Integer
}

// Define a structure to represent a hint for a specific cell.
structure CellHint {
    @documentation("The coordinates of the cell.")
    @required
    emptyCell: EmptyCell

    @required
    @documentation("The possible digits for the cell.")
    possibleDigits: IntegerList
}


@mixin
structure GetHintsRequest {
    @required
    trace: String
    @documentation("The number of hints to retrieve.")
    hintCount: Integer
}

structure EmptyCellHintsMetadata {
    @documentation("The total number of empty cells.")
    @required
    totalEmptyCells: Integer
}

structure EmptyCellHints {
    @documentation("A list of hints, each containing cell coordinates and possible digits.")
    @required
    hints: CellHintList

    @documentation("Metadata about the empty cells.")
    @required
    metadata: EmptyCellHintsMetadata
}