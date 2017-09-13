package examples

import artie._
import artie.implicits._

import util._

object IgnoreFieldsSpec extends suite.RefactoringSpec("ignore-fields-provider") {

  import PlayJsonToRead._

  implicit val ignoreUserName = IgnoreFields[User].ignore('name)

  val conf = Config("http://localhost", 9000, "http://localhost", 9001)

  val providers = Providers ~
    ('ids, provide[Int].random(10, 100))

  check("get", providers, conf, read[User]) { implicit r => p =>
    val id = select('ids, p).next

    get(s"/user/$id")
  }
}
