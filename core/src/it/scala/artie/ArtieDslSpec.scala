package artie

import artie.implicits._

import akka.actor.ActorSystem
import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

final class ArtieDslSpec(implicit ee: ExecutionEnv) extends Specification {

  sequential

  implicit val system = ActorSystem("artie_dsl_it_spec")

  val conf = config("http://localhost", 9000, "http://localhost", 9001)
    .repetitions(1000)
    .parallelism(5)
    .stopOnFailure(false)

  val prs = Providers ~ ('ids, provide[Int].static(0, 1, 2, 3))

  val succServerA1 = new SuccessfulServerA(9000)
  val succServerA2 = new SuccessfulServerA(9001)
  val succServerB  = new SuccessfulServerB(9002)
  val failedServer = new FailingServer(9003)

  "Artie dsl integration test" >> {
    "no failures nor invalids" >> {
      check(prs, conf, User.read) { implicit r => p =>
        val id = select('ids, p).next

        get(s"/test/$id")
      } must beEqualTo(TestState(conf.repetitions, 0, 0)).awaitFor(5.second)
    }

    "invalids" >> {
      val c = conf.copy(basePort = 9003, refactoredPort = 9003)

      check(prs, c, User.read) { implicit r => p =>
        val id = select('ids, p).next

        get(s"/test/$id")
      }.map(_.copy(reasons = Nil)) must beEqualTo(TestState(0, conf.repetitions, 0)).awaitFor(5.second)
    }

    "failed by status code" >> {
      val c = conf.copy(basePort = 9003)

      check(prs, c, User.read) { implicit r => p =>
        val id = select('ids, p).next

        get(s"/test/$id")
      }.map(_.copy(reasons = Nil)) must beEqualTo(TestState(0, 0, conf.repetitions)).awaitFor(5.second)
    }

    "failed by difference" >> {
      val c = conf.copy(basePort = 9002)

      check(prs, c, User.read) { implicit r => p =>
        val id = select('ids, p).next

        get(s"/test/$id")
      }.map(_.copy(reasons = Nil)) must beEqualTo(TestState(0, 0, conf.repetitions)).awaitFor(5.second)
    }

    "await TestState" >> {
      checkAwait(prs, conf.repetitions(1), User.read) { implicit r => p =>
        val id = select('ids, p).next

        get(s"/test/$id")
      }(1.second) === TestState(1, 0, 0)
    }

    "print failures" >> {
      val c = conf.copy(basePort = 9002, repetitions = 1)

      val state = checkAwait(prs, c, Group.read) { implicit r => p =>
        val id = select('ids, p).next

        put(s"/test/$id", contentO = Some(Json.toJson(Seq(User(26, "Paul"))).toString))
      }(1.second)

      printReasons(state)

      state.copy(reasons = Nil) === TestState(0, 0, 1)
    }

    step {
      val unbindsF = Future.sequence(Seq(
        succServerA1.stop(),
        succServerA2.stop(),
        succServerB.stop(),
        failedServer.stop()
      ))

      Await.ready(unbindsF.map(_ => system.terminate()), 5.seconds)
    }
  }
}
