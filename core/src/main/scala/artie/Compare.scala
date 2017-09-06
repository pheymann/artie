package artie

trait Compare[A] extends ((A, A) => Seq[FieldDiff]) {

  def active = true

  protected def diff[B](field: String)(l: B, r: B)(implicit equ: Eq[B] = Eq.default[B]): Option[FieldDiff] =
    if (equ(l, r)) None
    else           Some(FieldDiff(field, l, r))

  def compare(l: A, r: A): Seq[Option[FieldDiff]]

  def apply(l: A, r: A): Seq[FieldDiff] = compare(l, r).flatten
}

object Compare {

  def default[A] = new Compare[A] {
    override def active = false

    def compare(l: A, r: A) = Nil
  }
}
