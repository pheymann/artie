package single

import artie._
import artie.implicits._

import util._

object BasicSpec extends RefactoringSpec("my test service") {

  import PlayJsonToRead._

  val conf = config("http://localhost", 9000, "http://localhost", 9001)

  val providers = Providers ~
    ('ids, provide[Long].static(0L, 1L, 2L))

  check("get-user-failing", providers, conf, read[User]) { implicit r => p =>
    val id = select('ids, p).next

    get(s"/user/$id")
  }

  check("get-user-success", providers, otherServer(conf), read[User]) { implicit r => p =>
    val id = select('ids, p).next

    get(s"/user/$id")
  }

  private def otherServer(conf: TestConfig) = conf.copy(basePort = 9001)
}
