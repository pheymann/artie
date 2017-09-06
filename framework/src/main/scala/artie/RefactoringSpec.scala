package artie

import shapeless._

import scala.util.Random
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

final case class LazyCheck(run: Unit => Future[TestState])

abstract class RefactoringSpec(service: String) {

  private var isSequential = true

  def parallel: Unit = isSequential = false

  private val checks = Seq.newBuilder[(String, LazyCheck)]
  private val rand   = new Random(System.currentTimeMillis())

  def check[P <: HList, A, H <: HList](endpoint: String, providersF: Future[P], config: TestConfig, read: Read[A])
                                      (f: Random => P => RequestT)
                                      (implicit gen: LabelledGeneric.Aux[A, H], genDiff: Lazy[GenericDiff[H]]): Unit = {
    checks += (endpoint -> LazyCheck(_ => artie.check(providersF, config, read, rand)(f)))
  }

  def waitTimeout = 10.minutes

  def main(args: Array[String]): Unit = {
    println(s"testing refactorings for $service:")

    val statesF = {
      if (isSequential) {
        def runInSequence(remaining: Seq[(String, LazyCheck)], acc: Seq[(String, TestState)]): Future[Seq[(String, TestState)]] = remaining match {
          case (endpoint, check) +: tail =>
            println(s"  + check $endpoint")

            check.run().flatMap { state =>
              if (state.isFailed)
                println("    failed with:")

              printReasons(state)              
              runInSequence(tail, (endpoint -> state) +: acc)
            }

          case Nil => Future.successful(acc)
        }

        runInSequence(checks.result(), Nil)
      }
      else {
        val stateFs = checks.result().map {
          case (endpoint, check) =>
            println(s"  + check $endpoint")

            check.run().map(state => (endpoint -> state))
        }

        val statesF = Future.sequence(stateFs)

        statesF.foreach(_.foreach { case (endpoint, state) =>
          if (state.isFailed) {
            println(s"  - $endpoint failed with :")
            printReasons(state)
          }
        })
        statesF
      }
    }

    val states     = Await.result(statesF, waitTimeout)
    val totalState = states.foldLeft(TestState(0, 0, 0)) { case (acc, (endpoint, state)) =>
      acc <+> state
    }

    printState(totalState)

    if (totalState.isFailed)
      System.exit(1)
    else
      System.exit(0)
  }
}
