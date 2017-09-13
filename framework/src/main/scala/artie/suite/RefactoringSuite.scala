package artie.suite

import artie._

import artie.Util._

import scala.concurrent.Await
import scala.concurrent.duration._

/** Framework to collect multiple `suite.RefactoringSpec`s and execute them together. */
abstract class RefactoringSuite {

  def specs: Seq[RefactoringSpec]

  def waitTimeout = 10.minutes

  def main(args: Array[String]): Unit = {
    // lets start with sequential execution
    val totalState = specs.foldLeft(TestState(0, 0, 0)) { case (acc, spec) =>
      val state = Await.result(runSpec(spec), waitTimeout)

      acc <+> state
    }

    printState(totalState)

    if (totalState.isFailed)
      System.exit(1)
  }
}
