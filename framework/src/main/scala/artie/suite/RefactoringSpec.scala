package artie.suite

import artie._
import artie.Util._

import shapeless._

import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.global

abstract class RefactoringSpec(val service: String) {

  implicit val ec = global

  var isSequential = true

  def parallel: Unit = isSequential = false

  val checks = Seq.newBuilder[(String, LazyCheck)]

  protected val rand = new Random(System.currentTimeMillis())

  def check[P <: HList, A, H <: HList](endpoint: String, providersF: Future[P], config: TestConfig, read: Read[A])
                                      (f: Random => P => RequestT)
                                      (implicit gen: LabelledGeneric.Aux[A, H],
                                                genDiff: Lazy[GenericDiff[H]],
                                                comp: Compare[A] = Compare.default[A]): Unit = {
    checks += (endpoint -> LazyCheck(() => artie.check(providersF, config, read, rand)(f)))
  }
}
