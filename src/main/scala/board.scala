import scala.io.Source
import scala.math._

case class board (path: String) {

  var arr:Seq[String] = Seq()

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

  def solve(board: Seq[Seq[Int]]):Boolean = {
    true
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

  def align(row: Seq[Int]):Unit = {
    row.foreach((i: Int) => i match{
      case 0 => print(". ")
      case _ => print(i + " ")
    })
    println
  }

  def preview(b:Seq[Seq[Int]]):Unit={
    b.foreach(
      align(_)
    )

    printf("\n\n")
  }
}

object Test{
  def main(args: Array[String]): Unit = {
    val build = board("./src/main/resources/trace") // relative to the root dir.

    // overview: run the engine against a sequence of solver algorithms
    val solvers:Seq[algorithm] = Seq(backtracking)
    build.engine(build.path, solvers)

    // solvers.foreach((i:algorithm)=> build.engine(build.path, i))
  }

}
