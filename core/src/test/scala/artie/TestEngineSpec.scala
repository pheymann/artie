package artie

import artie.TestEngine._
import artie.implicits._

import scalaj.http._
import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

import scala.concurrent.Future
import scala.concurrent.duration._

final class TestEngineSpec(implicit ee: ExecutionEnv) extends Specification {

  val read = new Read[User] {
    def apply(raw: String): Either[String, User] = {
      val Array(name, age) = raw.split(",")

      if (name == "fail")
        Left("expected")
      else
        Right(User(name, age.toInt))
    }
  }

  "TestEngine" >> {
    "RequestT to HttpRequest" >> {
      toHttpRequest("base", get("/uri")) === Http("base/uri")
      toHttpRequest("base", post("/uri", contentO = Some("content"))) === Http("base/uri").postData("content")
      toHttpRequest("base", put("/uri", contentO = Some("content"))) === Http("base/uri").put("content")
      toHttpRequest("base", delete("/uri")) === Http("base/uri").method("DELETE")
    }

    "compare HttpResponses from base and refactored" >> {
      val initState = TestState(0, 0, 0)

      val req   = get("/test")
      val resp0 = HttpResponse("John,10", 200, Map.empty)
      val resp1 = HttpResponse("Jo,20", 200, Map.empty)

      compareResponses(req, resp0, resp0, read, initState, 1) === TestState(1, 0, 0)
      compareResponses(req, resp0.copy(code = 404), resp0.copy(code = 500), read, initState, 1) === TestState(0, 1, 0, Seq(
        ResponseCodeDiff(req, "invalid:\n  HttpResponse(John,10,404,Map())\n  HttpResponse(John,10,500,Map())"))
      )
      compareResponses(req, resp0.copy(code = 404), resp0, read, initState, 1) === TestState(0, 0, 1, Seq(
        ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())"))
      )
      compareResponses(req, resp0, resp0.copy(code = 404), read, initState, 1) === TestState(0, 0, 1, Seq(
        ResponseCodeDiff(req, "refactored service status error:\n  HttpResponse(John,10,404,Map())"))
      )
      compareResponses(req, resp0, resp1, read, initState, 1).copy(reasons = Nil) === TestState(0, 0, 1, Nil)

      val previousState = TestState(0, 0, 1, Seq(ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())")))
      compareResponses(req, resp0.copy(code = 405), resp0, read, previousState, 1) === TestState(0, 0, 2, Seq(
        ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())"))
      )
    }

    "run test cases in batches of size `n`" >> {
      import shapeless._
      import scala.util.Random

      val conf = Config("base", 0, "ref", 1)

      val prov0 = provide[Unit].static(())
      val provs = Providers ~ ('ignore, prov0)

      val resp0 = HttpResponse("John,10", 200, Map.empty)

      def gen[P <: HList](pf: Future[P]): Random => P => RequestT = 
        _ => _ => get("test")

      run(null, provs, conf, gen(provs), read, _ => resp0) must beEqualTo(TestState(1, 0, 0)).awaitFor(1.second)
      run(null, provs, conf.repetitions(10), gen(provs), read, _ => resp0) must beEqualTo(TestState(10, 0, 0)).awaitFor(1.second)
      run(null, provs, conf.repetitions(10).parallelism(2), gen(provs), read, _ => resp0) must beEqualTo(TestState(10, 0, 0)).awaitFor(1.second)

      val fakeIo: HttpRequest => HttpResponse[String] = {
        var counter = 0

        request => {
          if (counter == 0) {
            counter += 1
            resp0.copy(code = 500)
          }
          else
            resp0
        }
      }

      run(null, provs, conf.repetitions(2).stopOnFailure(true), gen(provs), read, fakeIo).map(_.copy(reasons = Nil)) must beEqualTo(TestState(0, 0, 1)).awaitFor(1.second)
    }
  }
}
