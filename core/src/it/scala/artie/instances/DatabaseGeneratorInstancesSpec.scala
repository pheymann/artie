package artie.instances

import artie.Util

import org.specs2.mutable.Specification

final class DatabaseGeneratorInstancesSpec extends Specification {

  object autoimport extends DatabaseGeneratorInstances with DatabaseColumnReaderInstances

  import autoimport._

  val table = "dbis_table"

  val createTable =
    s"""CREATE TABLE $table (
       |  l BIGINT PRIMARY KEY,
       |  s VARCHAR(10),
       |  d DOUBLE,
       |  f FLOAT,
       |  i INT,
       |)""".stripMargin

  val insertRows =
    s"INSERT INTO $table (l, s, d, f, i) VALUES (0, 'hello', 0.0, 1.0, 1)"

  val db = artie.h2("mem:test_db;DB_CLOSE_DELAY=-1;MODE=MySQL", "user", "pwd")

  val query = s"select * from $table"

  "DatabaseGenerator instances" >> {
    step {
      Util.prepare(db, Seq(createTable, insertRows))
    }

    "read single values" >> {
      dbSingleGen[Int].apply(query, db) === Seq(0)
      dbSingleGen[Long].apply(query, db) === Seq(0L)
      dbSingleGen[Float].apply(query, db) === Seq(0.0f)
      dbSingleGen[Double].apply(query, db) === Seq(0.0)
      dbSingleGen[String].apply(query, db) === Seq("0")
    }

    "read tuples of different sizes" >> {
      dbTuple2Gen[Long, String].apply(query, db) === Seq((0L, "hello"))
      dbTuple3Gen[Long, String, Double].apply(query, db) === Seq((0L, "hello", 0.0))
      dbTuple4Gen[Long, String, Double, Float].apply(query, db) === Seq((0L, "hello", 0.0, 1.0f))
      dbTuple5Gen[Long, String, Double, Float, Int].apply(query, db) === Seq((0L, "hello", 0.0, 1.0f, 1))
    }

    "read generic nested tuple n" >> {
      dbSingleGen[(Long, (String, Double))].apply(query, db) === Seq((0L, ("hello", 0.0)))
    }
  }
}
