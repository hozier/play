import scala.io.Source
import scala.math._

/*
overview: a very loosely defined abstraction of the game board.

the case class board can represent a set of boards or one board.
the board itself is quite ephemeral -- as the case class does
not retain the state of the board beyond the board's initial return
from the class's parser. functional programs should be stateless, and as
such, the case class retains no knowledge of the board beyond it's
being passed to the callback algorithm(s)

it's unbounded abstraction keeps its definition open
and allows the developer to leverage the interplay between
a loosely defined object and the class's API.

*/
case class board (path: String) {

  def parse(str: String):Seq[Seq[Int]] ={
    val arr:Seq[Seq[Int]] = Seq()
    if(str.contains('=')) null // we are reading the delim
    else cut(str, arr, sqrt(str.length).toInt)
  }

  /*
  // overview: considers format delim by newline characters
  def sew(str:String, arr:Seq[Seq[Int]]):Seq[Seq[Int]] = {
    null
  }
  */

  // overview: implements IO
  // parses into board structure,
  // previews board to be solved to standard out,
  // passes to solver
  def engine(file:String, algorithms: Seq[algorithm]):Unit ={

    for( line <- Source.fromFile(file).getLines()){
      parse(line) match {
        case null => println("Reading new board..")
        case board => {
          preview(board) //overview: preview the board to be solved.
          algorithms.foreach((i:algorithm)=> i.solve(board))// called through the algorithm selection engine
        }
      } // end case match.
    }
  }

  // overview: parses linear string
  // takes the sqrt of the linear string's length,
  // ie sqrt(str.length) * sqrt(str.length) = N * N =  str.length
  // returns Seq of rows
  def cut(str: String, arr:Seq[Seq[Int]], n:Int):Seq[Seq[Int]] = {
    if(str.isEmpty) return arr//.filter(!_.isEmpty)

    val s:String = str.substring(0, n)
    val row = s.map( x=> x.toInt - 48).toSeq

    cut(str.substring(n),
    /*new Seq w appended r*/
    arr :+ row, n)
  }

  // overview: helper: previews the current board's state
  def align(row: Seq[Int]):Unit = {
    row.foreach((i: Int) => i match{
      case 0 => print(". ")
      case _ => print(i + " ")
    })
    println
  }
  // overview: previews the current board's state
  def preview(b:Seq[Seq[Int]]):Unit={
    b.foreach(
      align(_)
    )
    printf("\n\n")
  }
}
