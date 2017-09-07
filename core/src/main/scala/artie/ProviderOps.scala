package artie

import shapeless._
import shapeless.labelled._

import scala.concurrent.{Future, ExecutionContext}

trait ProviderOps {

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
}
