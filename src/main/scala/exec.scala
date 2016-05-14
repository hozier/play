/* overview: run the engine against a sequence
   of solver algorithms
*/

object exec{

  // overview: analysis callback
  private def analysis(i:algorithm, board:Seq[Seq[Int]]):Unit ={
    val start = System.nanoTime
    val r = "\n\n\n\n\n"

    // run analysis iff the algorithm returns a validated solution
    if(i.solve(board)){
      val stop = System.nanoTime
      printf("Algorithm:\n%s\nElapsed: \n[%fs]%s",
        i.toString.split('$')(0), (stop - start)/1000000000.0, r)
    } else printf("[No solution]%s", r)
  }


  def main(args: Array[String]): Unit = {
    val builder = board("./src/main/resources/trace") // parses representation of game board, passes to solver(s)
    val solvers:Seq[algorithm] = Seq(backtracking)
    builder.engine(builder.path, solvers, analysis)

    // solvers.foreach((i:algorithm)=> build.engine(build.path, i)) // alternate.
  }

}
