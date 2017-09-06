package artie

import org.specs2.mutable.Specification

import java.sql.ResultSet

final class DatabaseGeneratorSpec extends Specification {

  import DatabaseGenerator._

  val createTable =
    """CREATE TABLE test_table (
      |  id INT PRIMARY KEY
      |)""".stripMargin

  val insertRows =
    "INSERT INTO test_table (id) VALUES (0)"

  val config = DatabaseConfig(Util.h2, "", "user", "pwd")

  "DatabaseGenerator" >> {
    step {
      Util.prepare(config, Seq(createTable, insertRows))
    }

    "run queries against a given database with a new connection" >> {
      val mapper: ResultSet => Int = rows => {
        rows.getInt(1)
      }

      runQuery("select * from test_table", config)(mapper) === Seq(0)
    }
  }
}
