package artie

import RandomGenerator._

import scala.annotation.tailrec
import scala.util.Random
import scala.concurrent.{ExecutionContext, Future}

trait DataSelector[A] {

  def next(implicit rand: Random): A

  final def nextOpt(implicit rand: Random, exists: Rand[Unit, Boolean]): Option[A] =
    if (exists(rand)(())) Some(next)
    else                  None

  @tailrec
  final def nextSeq(length: Int, acc: Seq[A] = Nil)(implicit rand: Random): Seq[A] = 
    if (length == 0) acc
    else             nextSeq(length - 1, next +: acc)
}

final class DataSelectorOps[A] {

  def static(data: A*)(implicit randF: Rand[Int, Int]) = new DataSelector[A] {
    def next(implicit rand: Random): A =
      data(randF(rand)(data.length))
  }

  def random(min: A, max: A)
            (implicit gen: RandomGenerator[A], randF: Rand[Unit, Double]) = new DataSelector[A] {
    def next(implicit rand: Random): A = gen(min, max, randF(rand))
  }

  import DatabaseGenerator._

  def database(query: String, config: DatabaseConfig)
              (implicit gen: DatabaseGenerator[A], ec: ExecutionContext, randF: Rand[Int, Int]): Future[DataSelector[A]] =
    Future(static(gen(query, config): _*))

  object database {

    def random(table: String, column: String, limit: Int, config: DatabaseConfig)
              (implicit gen: DatabaseGenerator[A], ec: ExecutionContext, randF: Rand[Int, Int]): Future[DataSelector[A]] =
      Future(static(gen(config.database.randomQuery(table, column, limit), config): _*))
  }
}
