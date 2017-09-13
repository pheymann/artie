package artie

import scala.util.Random

import java.sql.{ResultSet, Connection, Statement, DriverManager}

/** Type trait for data generators (source of providers). */
trait DataGenerator

/** Randomly generates data of type `A`. */
trait RandomGenerator[A] extends DataGenerator {

  def apply(min: A, max: A, rand: Unit => Double): A
}

object RandomGenerator {

  type Rand[A, B] = Random => A => B
}

/** Queries data from some database. */
trait DatabaseGenerator[A] extends DataGenerator {

  import DatabaseGenerator._

  def apply(query: String, db: Database): Seq[A]
}

object DatabaseGenerator {

  /** Database descriptor. */
  trait Database {

    def driver: String
    def host: String
    def user: String
    def password: String
    def qualifiedHost: String
    def randomQuery(table: String, column: String, limit: Int): String
  }

  def runQuery[A](query: String, db: Database)(f: ResultSet => A): Seq[A] = {
    Class.forName(db.driver)

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

    withConnection(DriverManager.getConnection(db.qualifiedHost, db.user, db.password))
  }
}
