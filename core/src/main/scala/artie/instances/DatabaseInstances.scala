package artie.instances

import artie.DatabaseGenerator.Database

abstract class MySql extends Database {

  val driver = "com.mysql.jdbc.Driver"

  def qualifiedHost = "jdbc:mysql://" + host

  def randomQuery(table: String, column: String, limit: Int): String =
    s"""SELECT DISTINCT t.$column
       |FROM $table AS t
       |ORDER BY RAND()
       |LIMIT $limit
       |""".stripMargin
}

abstract class H2 extends Database {

  val driver = "org.h2.Driver"

  def qualifiedHost = "jdbc:h2:" + host

  def randomQuery(table: String, column: String, limit: Int): String =
    s"""SELECT DISTINCT t.$column
       |FROM $table AS t
       |ORDER BY RAND()
       |LIMIT $limit
       |""".stripMargin
}

abstract class PostgresSql extends Database {

  val driver = "org.postgresql.Driver"

  def qualifiedHost = "jdbc:postgresql://" + host

  def randomQuery(table: String, column: String, limit: Int): String =
    s"""SELECT t.$column
       |FROM $table AS t
       |ORDER BY RANDOM()
       |LIMIT $limit
       |""".stripMargin
}

trait DatabaseInstanceOps {

  def mysql(_host: String, _user: String, _password: String) = new MySql {
    val host = _host
    val user = _user
    val password = _password
  }

  def h2(_host: String, _user: String, _password: String) = new H2 {
    val host = _host
    val user = _user
    val password = _password
  }

  def postgres(_host: String, _user: String, _password: String) = new PostgresSql {
    val host = _host
    val user = _user
    val password = _password
  }
}
