package artie

import org.specs2.mutable.Specification

import java.sql.ResultSet

final class DatabaseGeneratorSpec extends Specification {

  val createTable =
    """CREATE TABLE test_table (
      |  id INT PRIMARY KEY
      |)""".stripMargin

  val insertRows =
    "INSERT INTO test_table (id) VALUES (0)"

  val db = h2("mem:test_db;DB_CLOSE_DELAY=-1;MODE=MySQL", "user", "pwd")

  "DatabaseGenerator" >> {
    step {
      Util.prepare(db, Seq(createTable, insertRows))
    }

    "run queries against a given database with a new connection" >> {
      val mapper: ResultSet => Int = rows => {
        rows.getInt(1)
      }

      DatabaseGenerator.runQuery("select * from test_table", db)(mapper) === Seq(0)
    }
  }
}
