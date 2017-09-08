
import artie.instances.DatabaseInstanceOps

import shapeless._
import shapeless.ops.record._
import scalaj.http._

import scala.util.Random
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration.FiniteDuration

package object artie extends Request with ProviderOps with TestConfigOps with DatabaseInstanceOps with TestStateOps {

  import artie.DataSelectorOps

  def provide[A] = new DataSelectorOps[A]

  val Providers = HNil

  private def initRandom = new Random(System.currentTimeMillis() % 10000)

  def select[P <: HList](name: Witness, providers: P)(implicit select: Selector[P, name.T]): select.Out = select(providers)

  def check[P <: HList, A, H <: HList](providersF: Future[P], config: TestConfig, read: Read[A], rand: Random = initRandom, ioEffect: HttpRequest => HttpResponse[String] = _.asString)
                                      (f: Random => P => RequestT)
                                      (implicit ec: ExecutionContext, gen: LabelledGeneric.Aux[A, H], genDiff: Lazy[GenericDiff[H]]): Future[TestState] =
    TestEngine.run(rand, providersF, config, f, read, ioEffect)

  def checkAwait[P <: HList, A, H <: HList](providersF: Future[P], config: TestConfig, read: Read[A], rand: Random = initRandom, ioEffect: HttpRequest => HttpResponse[String] = _.asString)
                                           (f: Random => P => RequestT)
                                           (duration: FiniteDuration)
                                           (implicit ec: ExecutionContext, gen: LabelledGeneric.Aux[A, H], genDiff: Lazy[GenericDiff[H]]): TestState =
    Await.result(check(providersF, config, read, rand, ioEffect)(f), duration)

  def printReasons(state: TestState): Unit =
    if (state.reasons.nonEmpty)
      println(GenericDiffOps.mkString(state.reasons, "   "))

  def printState(state: TestState): Unit = {
    val resultStr = s"Total: ${state.total}, Succeeded: ${state.succeeded}, Invalid: ${state.invalid}, Failed: ${state.failed}" 

    if (state.isFailed) println("Failed: " + resultStr)
    else                println("Success: " + resultStr)
  }
}
