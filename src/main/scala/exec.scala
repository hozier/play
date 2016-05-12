/* overview: run the engine against a sequence
   of solver algorithms
*/

object exec{
  def main(args: Array[String]): Unit = {
    val builder = board("./src/main/resources/trace") // parses representation of game board, passes to solver(s)
    val solvers:Seq[algorithm] = Seq(backtracking)
    builder.engine(builder.path, solvers)

    // solvers.foreach((i:algorithm)=> build.engine(build.path, i)) // alternate.
  }

}
