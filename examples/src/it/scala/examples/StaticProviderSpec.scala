package examples

import artie._
import artie.implicits._

import util._

import play.api.libs.json._

object StaticProviderSpec extends suite.RefactoringSpec("static-provider") {

  import PlayJsonToRead._

  val conf = Config("http://localhost", 9000, "http://localhost", 9001)

  val providers = Providers ~
    ('ids, provide[Long].static(0L, 1L, 2L)) ~
    ('users, provide[User].static(User(0, "Joe"), User(1, "Foo")))

  check("get", providers, conf, read[User]) { implicit r => p =>
    val id = select('ids, p).next

    get(s"/user/$id")
  }

  check("post", providers, conf, read[TopicToUser]) { implicit r => p =>
    val id   = select('ids, p).next
    val user = select('users, p).next

    post(s"/user/$id", headers = Headers <&> ("Content-Type", "application/json"), contentO = Some(Json.toJson(user).toString))
  }  
}
