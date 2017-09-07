
import shapeless._
import shapeless.labelled._
import shapeless.ops.record._
import scalaj.http._

import scala.util.Random
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration.FiniteDuration

package object artie extends Request with TestConfigOps with TestStateOps {

  import artie.DatabaseGenerator.Database

  object mysql extends Database {

    val driver = "com.mysql.jdbc.Driver"

    def qualifiedHost(host: String) = "jdbc:mysql://"

    def randomQuery(table: String, column: String, limit: Int): String =
      s"""SELECT DISTINCT t.$column
          |FROM $table AS t
          |ORDER BY RAND()
          |LIMIT $limit
          |""".stripMargin
  }

  import artie.{DataSelector, DataSelectorOps}

  def provide[A] = new DataSelectorOps[A]

  val Providers = HNil

  implicit class ProviderListOps[P <: HList](providers: P) {
    def ~[A](name: Witness, selector: DataSelector[A]): Future[FieldType[name.T, DataSelector[A]] :: P] =
      Future.successful(field[name.T](selector) :: providers)

    def ~[A](name: Witness, selectorF: Future[DataSelector[A]])(implicit ec: ExecutionContext): Future[FieldType[name.T, DataSelector[A]] :: P] =
      selectorF.map { selector =>
        field[name.T](selector) :: providers
      }
  }

  implicit class FutureProviderListOps[P <: HList](providersF: Future[P]) {
    def ~[A](name: Witness, selector: DataSelector[A])(implicit ec: ExecutionContext): Future[FieldType[name.T, DataSelector[A]] :: P] =
      providersF.map { providers =>
        field[name.T](selector) :: providers
      }

    def ~[A](name: Witness, selectorF: Future[DataSelector[A]])(implicit ec: ExecutionContext): Future[FieldType[name.T, DataSelector[A]] :: P] =
      for {
        providers <- providersF
        selector  <- selectorF
      } yield field[name.T](selector) :: providers
  }

  trait Read[A] extends (String => Either[String, A])

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
      println(GenericDiffOps.mkString(state.reasons))

  def printState(state: TestState): Unit = {
    val resultStr = s"Total: ${state.total}, Succeeded: ${state.succeeded}, Invalid: ${state.invalid}, Failed: ${state.failed}" 

    if (state.isFailed) println("Failed: " + resultStr)
    else                println("Success: " + resultStr)
  }
}
