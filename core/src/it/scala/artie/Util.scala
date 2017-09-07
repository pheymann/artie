package artie

import java.sql.{Statement, DriverManager, Connection}

object Util {

  import DatabaseGenerator._

  object h2 extends Database {

    val driver = "org.h2.Driver"

    def qualifiedHost(host: String) = "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1;MODE=MySQL"

    def randomQuery(table: String, column: String, limit: Int): String =
      s"""SELECT DISTINCT t.$column
          |FROM $table AS t
          |ORDER BY RAND()
          |LIMIT $limit
          |""".stripMargin
  }

  def prepare(config: DatabaseConfig, queries: Seq[String]): Unit = {
    import config._

    Class.forName(database.driver)

    def withPrepared(conn: Connection): Unit =
      try {
        def withUpdate(stmt: Statement, query: String): Unit =
          try {            
            stmt.executeUpdate(query)
          }
          finally {
            stmt.close
          }

        queries.foreach(withUpdate(conn.createStatement(), _))
      }
      finally {
        conn.close()
      }

    withPrepared(DriverManager.getConnection(database.qualifiedHost(host), user, password))
  }
}
