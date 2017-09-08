package examples

import artie._
import artie.implicits._

import util._

object IgnoreFieldsSpec extends RefactoringSpec("ignore-fields-provider") {

  import PlayJsonToRead._

  // ignore the name by not listing it
  implicit val userComp = new Compare[User] {
    def compare(l: User, r: User) = Seq(
      diff("id")(l.id, r.id)
    )
  }

  val conf = config("http://localhost", 9000, "http://localhost", 9001)

  val providers = Providers ~
    ('ids, provide[Int].random(10, 100))

  check("get", providers, conf, read[User]) { implicit r => p =>
    val id = select('ids, p).next

    get(s"/user/$id")
  }
}
