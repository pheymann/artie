package artie

import scala.annotation.implicitNotFound

@implicitNotFound("Cannot find Eq instance for Type ${A}")
trait Eq[A] extends {

  @inline def apply(l: A, r: A): Boolean

  @inline def not(l: A, r: A) = !(this.apply(l, r))
}

object Eq {

  def default[A] = new Eq[A] {
    def apply(l: A, r: A): Boolean = l == r
  }

  def from[A](f: (A, A) => Boolean): Eq[A] = new Eq[A] {
    def apply(l: A, r: A): Boolean = f(l, r)
  }
}
