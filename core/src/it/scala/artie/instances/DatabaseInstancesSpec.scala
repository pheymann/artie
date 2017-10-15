package artie.instances

import artie.Util

import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

import scala.util.Random
import scala.concurrent.duration._

final class DatabaseInstancesSpec(implicit ee: ExecutionEnv) extends Specification {

  sequential

  object autoimport extends DatabaseGeneratorInstances with DatabaseColumnReaderInstances with RandomInstances

  import autoimport._

  implicit val random = new Random(0L)

  val table = "db_table2"

  val createTable =
    s"""CREATE TABLE $table (
       |  l BIGINT PRIMARY KEY,
       |)""".stripMargin

  val insertRows =
    s"INSERT INTO $table (l) VALUES (1), (2), (3), (4)"

  "database instances" >> {
    "random select" >> {
      "h2" >> {
        val db = artie.h2("mem:test_db;DB_CLOSE_DELAY=-1", "user", "pwd")

        Util.prepare(db, Seq(createTable, insertRows))

        artie.provide[Long].database.random(table, "l", 2, db).map(_.next) should beGreaterThan(0l).awaitFor(1.second)
      }

      "mysql" >> {
        val db = new MySql {
          val host = "mem:test_db;DB_CLOSE_DELAY=-1;MODE=MySQL"
          val user = "user"
          val password = "pwd"

          override val driver = "org.h2.Driver"
          override def qualifiedHost = "jdbc:h2:" + host
        }

        artie.provide[Long].database.random(table, "l", 2, db).map(_.next) should beGreaterThan(0l).awaitFor(1.second)
      }

      "postgres" >> {
        val db = new PostgresSql {
          val host = "mem:test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
          val user = "user"
          val password = "pwd"

          override val driver = "org.h2.Driver"
          override def qualifiedHost = "jdbc:h2:" + host
        }

        artie.provide[Long].database.random(table, "l", 2, db).map(_.next) should beGreaterThan(0l).awaitFor(1.second)
      }
    }
  }
}
