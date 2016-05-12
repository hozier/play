object backtracking extends algorithm{

  def solve(board: Seq[Seq[Int]]):Boolean = {
    if(end_of_grid(board)){
      return true
    }
    backtrack(((0,0), board))
    true
  }


  // overview: accepts the position of a currently empty cell
  def backtrack(parcel: ((Int, Int), Seq[Seq[Int]]) ):Boolean={
    val position = parcel._1
    val board = parcel._2

    // 1) try all positions from 1 to n.
    Range(0, board.length).foreach((i:Int)=> {
      // print(i+ " ")
      val mutating_board = board.updated(position._1, // update this row of board
        board(position._1) .updated((position._2), i)) // w this i index

      // overview: check for collisions w new board.
      is_valid_grid((position, mutating_board), i)


    })

    true


  }


  // overview: checks whether the position's units are still valid
  // row, column, boxStartRow
  def is_valid_grid(parcel: ((Int, Int), Seq[Seq[Int]]), number:Int): Boolean = {
    val position = parcel._1
    val board = parcel._2

    // overview: validate columns, validate rows
    if(!validate_primary(position._2, board.transpose:Seq[Seq[Int]], number)||
      !validate_primary(position._1, board, number)) return false

    // overview: check "box"

    true
  }

  def validate_primary(index:Int, board: Seq[Seq[Int]], number:Int):Boolean = {
    var count:Int = 0;
    board(index).foreach( (i:Int)=> (i == number) match {
      case true => {
        count += 1
        if(count > 1) return false
      }
      case false => count
    })
    true
  }


  // overview: done.
  def end_of_grid(board: Seq[Seq[Int]]):Boolean ={
    // find whether all cells are filled
    board.foreach(_.foreach( // for each list/row
      (cell:Int) => if(cell == 0) return false
    ))
    return true
  }
}
