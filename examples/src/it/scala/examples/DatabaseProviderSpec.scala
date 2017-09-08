package examples

import artie._
import artie.implicits._
import util._

object DatabaseProviderSpec extends RefactoringSpec("database-provider") {

  import PlayJsonToRead._

  val conf = config("http://localhost", 9000, "http://localhost", 9001)
  val db   = h2("mem:test_db;DB_CLOSE_DELAY=-1;MODE=MySQL", "user", "password")

  DbPreparation.prepare(db)

  val providers = Providers ~
    ('ids,   provide[Int].database("select id from users limit 3", db))
    ('names, provide[String].database.random("users", "name", 3, db))

  check("get", providers, conf, read[User]) { implicit r => p =>
    val id = select('ids, p).next
  
    get(s"/user/$id")
  }
}

// Only needed to prepare test data
object DbPreparation {

  import DatabaseGenerator.Database
  import java.sql.{Statement, DriverManager, Connection}

  val createTable =
    """CREATE TABLE users (
      |  id   INT PRIMARY KEY,
      |  name VARCHAR(100)
      |)""".stripMargin

  val insertRows =
    "INSERT INTO users (id, name) VALUES (0, 'Jim'), (1, 'Joe'), (2, 'Tim'), (3, 'Tom'), (4, 'Tick'), (5, 'Trick'), (6, 'Track')"

  def prepare(db: Database): Unit = {
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

        Seq(createTable, insertRows).foreach(withUpdate(conn.createStatement(), _))
      }
      finally {
        conn.close()
      }

    withPrepared(DriverManager.getConnection(db.qualifiedHost, db.user, db.password))
  }
}
