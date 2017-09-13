package artie

import shapeless._
import shapeless.ops.record._

/** Type class to specify fields that will be ignore during comparison. */
trait IgnoreFields[A] { self =>

  def fields: Set[Symbol]

  def ignore[H <: HList](field: Witness)
                        (implicit ev0: LabelledGeneric.Aux[A, H], ev1: Selector[H, field.T], ev2: field.T <:< Symbol): IgnoreFields[A] =
    new IgnoreFields[A] {
      val fields = self.fields + field.value
    }
}

object IgnoreFields {

  def apply[A] = new IgnoreFields[A] {
    val fields = Set.empty[Symbol]
  }
}
