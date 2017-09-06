package artie

import scala.util.Random

import java.sql.{ResultSet, Connection, Statement, DriverManager}

trait DataGenerator

trait RandomGenerator[A] extends DataGenerator {

  import RandomGenerator._

  def apply(min: A, max: A, rand: Unit => Double): A
}

object RandomGenerator {

  type Rand[A, B] = Random => A => B
}

trait DatabaseGenerator[A] extends DataGenerator {

  import DatabaseGenerator._

  def apply(query: String, config: DatabaseConfig): Seq[A]
}

object DatabaseGenerator {

  trait Database {

    def driver: String
    def qualifiedHost(host: String): String
    def randomQuery(table: String, column: String, limit: Int): String
  }

  final case class DatabaseConfig(database: Database, host: String, user: String, password: String)

  def runQuery[A](query: String, config: DatabaseConfig)(f: ResultSet => A): Seq[A] = {
    import config._

    Class.forName(database.driver)

    def withStatement(stmt: Statement): Seq[A] =
      try {
        val rows    = stmt.executeQuery(query)
        val builder = Seq.newBuilder[A]

        while (rows.next())
          builder += f(rows)

        builder.result()
      }
      finally {
        stmt.close()
      }

    def withConnection(conn: Connection): Seq[A] =
      try {
        withStatement(conn.createStatement())
      }
      finally {
        conn.close()
      }

    withConnection(DriverManager.getConnection(database.qualifiedHost(host), user, password))
  }
}
