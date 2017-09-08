package artie

import java.sql.{Statement, DriverManager, Connection}

object Util {

  import DatabaseGenerator._

  def prepare(db: Database, queries: Seq[String]): Unit = {
    Class.forName(db.driver)

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

    withPrepared(DriverManager.getConnection(db.qualifiedHost, db.user, db.password))
  }
}
