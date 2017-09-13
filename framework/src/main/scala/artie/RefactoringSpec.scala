package artie

import Util._

import scala.concurrent.Await
import scala.concurrent.duration._

/** Framework to build a stand-alone refactoring spec app (main included).
  * 
  * @_service name of the service which was refactored
  */
abstract class RefactoringSpec(_service: String) extends suite.RefactoringSpec(_service) {

  def waitTimeout = 10.minutes

  def main(args: Array[String]): Unit = {
    val totalState = Await.result(runSpec(this), waitTimeout)

    printState(totalState)

    if (totalState.isFailed)
      System.exit(1)
  }
}
