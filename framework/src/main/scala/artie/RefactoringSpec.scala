package artie

import Util._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class RefactoringSpec(_service: String) extends suite.RefactoringSpec(_service) {

  def waitTimeout = 10.minutes

  def main(args: Array[String]): Unit = {
    val totalState = Await.result(runSpec(this), waitTimeout)

    printState(totalState)

    if (totalState.isFailed)
      System.exit(1)
    else
      System.exit(0)
  }
}
