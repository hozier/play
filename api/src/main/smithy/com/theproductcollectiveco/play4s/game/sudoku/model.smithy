$version: "2"

namespace com.theproductcollectiveco.play4s.game.sudoku

use smithy4s.meta#vector
use com.theproductcollectiveco.play4s.game.sudoku.public#ComputeSudoku

@pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
@documentation("Game ID; should be a UUID")
string GameId

string Algorithm

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
list AlgorithmList {
    member: Algorithm
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
    strategies: AlgorithmList
}

structure SudokuComputationSummary with [GameMetadata] {
    solution: BoardState
}