package artie

import scala.concurrent.Future

object Util {

  case class LazyCheck(run: () => Future[TestState])

  def runSpec(spec: suite.RefactoringSpec): Future[TestState] = {
    import spec._

    println(s"testing refactorings for $service:")

    val statesF = {
      if (isSequential) {
        // run each check after the previous has finished
        def runInSequence(remaining: Seq[(String, LazyCheck)], acc: Seq[(String, TestState)]): Future[Seq[(String, TestState)]] = remaining match {
          case (endpoint, check) +: tail =>
            println(s" + check $endpoint")

            check.run().flatMap { state =>
              printReasons(state)
              println("")
              runInSequence(tail, (endpoint -> state) +: acc)
            }

          case Nil => Future.successful(acc)
        }

        runInSequence(checks.result(), Nil)
      }
      else {
        // start all checks
        val stateFs = checks.result().map {
          case (endpoint, check) =>
            println(s" + check $endpoint")

            check.run().map(state => (endpoint -> state))
        }

        val statesF = Future.sequence(stateFs)

        statesF.foreach(_.foreach { case (endpoint, state) =>
          if (state.isFailed) {
            println(s" - $endpoint failed with :")
            printReasons(state)
          }
        })
        statesF
      }
    }

    statesF.map(_.foldLeft(TestState(0, 0, 0)) { case (acc, (_, state)) =>
      acc <+> state
    })
  }
}
