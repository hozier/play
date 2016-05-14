import scala.math._

object backtracking extends algorithm{

  def solve(board: Seq[Seq[Int]]):Boolean = {
    if(end_of_grid(board)) return true
    return backtrack((next_position(board), board))
  }


  // overview: wrapper around board class' preview utility method
  def preview(mutating_board:Seq[Seq[Int]]):Unit={
    board(null).preview(mutating_board)
  }


  // overview: accepts the position of a currently empty cell
  def backtrack(parcel: ((Int, Int), Seq[Seq[Int]]) ):Boolean={
    if(parcel._1 == null){ preview(parcel._2); return true } // all cells solved.
    val position = parcel._1
    val board = parcel._2

    (1 to board.length).foreach((i:Int)=> {
      if(is_valid_grid((position, board), i)){
        val mutating_board = board.updated(position._1, // update this row of board
          board(position._1) .updated((position._2), i)) // w this i index

        // preview mutating_board
        // preview(mutating_board)

      // overview: check for collisions w new board.
        if(backtrack(next_position(mutating_board), mutating_board)) return true
      }
    })
    false
  }


  // overview: return next empty cell position
  def next_position(board: Seq[Seq[Int]]):(Int, Int) ={
    for((row,i) <- board.view.zipWithIndex) {
      for((cell,j) <- row.view.zipWithIndex) {
        if(cell == 0){ return (i, j) }
      }
    }
    null
  }


  // overview: find the starting position of the current cell's "box"
  def starting_position(position:(Int, Int), board:Seq[Seq[Int]]):(Int, Int)={
    val row = position._1
    val column = position._2
    (row - ((row) % sqrt(board.length).toInt),
    column - ((column) % sqrt(board.length).toInt))
  }


  // overview: compute and return box in isolation of grid using linear algebra
  def get_box(x: Int, y: Int, board:Seq[Seq[Int]]) = { board.slice(x, x+ sqrt(board.length).toInt).transpose.slice(y, y+sqrt(board.length).toInt).transpose }


  // overview: checks whether the position's units are still valid
  // row, column, boxStartRow
  def is_valid_grid(parcel: ((Int, Int), Seq[Seq[Int]]), number:Int): Boolean = {
    val position = parcel._1
    val board = parcel._2

    // overview: run validator on each cell of box
    val (x, y):(Int, Int) = starting_position(position, board)

    // overview: validate row, validate column, validate box
    validator(get_box(x,y, board).flatten, number) && validator(board(position._1), number) && validator(board.transpose.apply(position._2), number)
  }


  // overview: validates row, column and box.
  def validator(row: Seq[Int], number:Int):Boolean = !row.contains(number)


  // overview: find whether all cells are filled -- if 0 is present (true), return false
  def end_of_grid(board: Seq[Seq[Int]]):Boolean = !board.map( _.contains(0)).contains(true)
}
