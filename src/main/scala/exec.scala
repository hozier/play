/* overview: run the engine against a sequence
   of solver algorithms
*/

object exec{

  // overview: analysis callback
  private def analysis(i:algorithm, board:Seq[Seq[Int]]):Unit ={
    val start = System.nanoTime

    // run analysis iff the algorithm returns a validated solution
    if(i.solve(board)){
      val stop = System.nanoTime
      printf("Algorithm:\n%s\nElapsed: \n[%fs]\n\n\n\n\n",
        i.toString.split('$')(0), (stop - start)/1000000000.0)
    } else printf("[No solution]\n\n\n\n\n")
  }



  def main(args: Array[String]): Unit = {
    val builder = board("./src/main/resources/trace") // parses representation of game board, passes to solver(s)
    val solvers:Seq[algorithm] = Seq(backtracking)
    builder.engine(builder.path, solvers, analysis)

    // solvers.foreach((i:algorithm)=> build.engine(build.path, i)) // alternate.
  }

}
