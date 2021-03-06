package artie.instances

import artie.DatabaseGenerator

import java.sql.ResultSet

trait DatabaseColumnReader[A] {
  def apply(rows: ResultSet)(index: Int): A
}

trait DatabaseColumnReaderInstances {

  implicit val colIntReader: DatabaseColumnReader[Int] = new DatabaseColumnReader[Int] {
    def apply(rows: ResultSet)(index: Int) = rows.getInt(index)
  }
  implicit val colLongReader: DatabaseColumnReader[Long] = new DatabaseColumnReader[Long] {
    def apply(rows: ResultSet)(index: Int) = rows.getLong(index)
  }
  implicit val colFloatReader: DatabaseColumnReader[Float] = new DatabaseColumnReader[Float] {
    def apply(rows: ResultSet)(index: Int) = rows.getFloat(index)
  }
  implicit val colDoubleReader: DatabaseColumnReader[Double] = new DatabaseColumnReader[Double] {
    def apply(rows: ResultSet)(index: Int) = rows.getDouble(index)
  }
  implicit val colStringReader: DatabaseColumnReader[String] = new DatabaseColumnReader[String] {
    def apply(rows: ResultSet)(index: Int) = rows.getString(index)
  }

  implicit def colPairReader[A, B](implicit colA: DatabaseColumnReader[A], colB: DatabaseColumnReader[B]) = new DatabaseColumnReader[(A, B)] {
    def apply(rows: ResultSet)(index: Int): (A, B) =
      (colA(rows)(index), colB(rows)(index + 1))
  }
}

trait DatabaseGeneratorInstances {

  import DatabaseGenerator._

  type Col[A] = DatabaseColumnReader[A]

  implicit def dbSingleGen[A](implicit colReader: Col[A]) = new DatabaseGenerator[A] {
    def apply(query: String, db: Database): Seq[A] =
      runQuery(query, db)(rows => colReader(rows)(1))
  }

  implicit def dbTuple2Gen[A, B](implicit colA: Col[A], colB: Col[B]) = new DatabaseGenerator[(A, B)] {
    def apply(query: String, db: Database): Seq[(A, B)] =
      runQuery(query, db)(rows => (colA(rows)(1), colB(rows)(2)))
  }

  implicit def dbTuple3Gen[A, B, C](implicit colA: Col[A], colB: Col[B], colC: Col[C]) = new DatabaseGenerator[(A, B, C)] {
    def apply(query: String, db: Database): Seq[(A, B, C)] =
      runQuery(query, db)(rows => (colA(rows)(1), colB(rows)(2), colC(rows)(3)))
  }

  implicit def dbTuple4Gen[A, B, C, D](implicit colA: Col[A],
                                                colB: Col[B],
                                                colC: Col[C],
                                                colD: Col[D]) = new DatabaseGenerator[(A, B, C, D)] {
    def apply(query: String, db: Database): Seq[(A, B, C, D)] =
      runQuery(query, db)(rows => (colA(rows)(1), colB(rows)(2), colC(rows)(3), colD(rows)(4)))
  }

  implicit def dbTuple5Gen[A, B, C, D, E](implicit colA: Col[A],
                                                   colB: Col[B],
                                                   colC: Col[C],
                                                   colD: Col[D],
                                                   colE: Col[E]) = new DatabaseGenerator[(A, B, C, D, E)] {
    def apply(query: String, db: Database): Seq[(A, B, C, D, E)] =
      runQuery(query, db)(rows => (colA(rows)(1), colB(rows)(2), colC(rows)(3), colD(rows)(4), colE(rows)(5)))
  }
}
