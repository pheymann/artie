package artie

import artie.RandomGenerator.Rand
import artie.DatabaseGenerator.Database
import artie.instances.RandomGeneratorInstances

import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv
import scalaj.http._

import scala.util.Random
import scala.concurrent.duration._

final class ArtieDslSpec(implicit ee: ExecutionEnv) extends Specification {

  object randomGen extends RandomGeneratorInstances with HighPriorityGenericDiff with MediumPriorityGenericDiffRunner

  import randomGen._


  implicit val testRand0: Rand[Int, Int] = _ => _ => 0
  implicit val testRand1: Rand[Unit, Double] = _ => _ => 0.5

  implicit val testDbGen = new DatabaseGenerator[Int] {
    def apply(query: String, db: Database) = Seq(1, 2, 3)
  }

  implicit val random: Random = null

  "artie provides a dsl to" >> {
    "create providers for static, random, db data" >> {
      provide[Int].static(0, 1, 2).next === 0
      provide[Int].random(0, 10).next === 5

      provide[Int].database("query", null).map(_.next) must beEqualTo(1).awaitFor(1.second)

      provide[Int].static(0).nextOpt(random, _ => _ => true) === Some(0)
      provide[Int].static(0).nextOpt(random, _ => _ => false) === None

      provide[Int].static(0, 1, 2).nextSeq(1) === Seq(0)
      provide[Int].static(0, 1, 2).nextSeq(2) === Seq(0, 0)

      provide[Int].static(0, 1, 2).nextSet(1) === Set(0)
      provide[Int].static(0, 1, 2).nextSet(2) === Set(0)
    }

    "concatenate providers and typesafe select one" >> {
      val p0 = provide[Double].static(-1.0, 2.0, -3.0)
      val p1 = provide[Int].database("query", null)

      val pf = Providers ~ ('a, p0) ~ ('b, p1)

      pf.map(p => select('a, p).next) must beEqualTo(-1.0).awaitFor(1.second)
    }

    "create a Map of request parameters" >> {
      Params === Map.empty[String, String]

      Params <&> ("0", "hello") <&> ("1", 10) <&> ("2", Some(true)) <&> ("2.1", None)  <&> ("3", Seq(1, 2, 3)) === Map(
        "0" -> "hello",
        "1" -> "10",
        "2" -> "true",
        "3" -> "1,2,3"
      )
    }

    "create a Map of request headers" >> {
      Headers === Map.empty[String, String]

      Headers <&> ("0", "hello") <&> ("1", 10) <&> ("2", Some(true)) <&> ("2.1", None)  <&> ("3", Seq(1, 2, 3)) <&> ("4", Set(1, 2, 3)) === Map(
        "0" -> "hello",
        "1" -> "10",
        "2" -> "true",
        "3" -> "1,2,3",
        "4" -> "1,2,3"
      )
    }

    "create requests" >> {
      get("uri") === (Get, "uri", Map.empty, Map.empty, None)
      put("uri") === (Put, "uri", Map.empty, Map.empty, None)
      post("uri") === (Post, "uri", Map.empty, Map.empty, None)
      delete("uri") === (Delete, "uri", Map.empty, Map.empty, None)
    }

    "create TestConfig" >> {
      Config("base", 0, "ref", 1) === TestConfig("base", 0, "ref", 1, 1, 1, true, 1, true)
      Config("base", 0, "ref", 1)
        .parallelism(10)
        .repetitions(100)
        .stopOnFailure(false)
        .shownDiffsLimit(10) === TestConfig("base", 0, "ref", 1, 100, 10, false, 10, true)
    }

    "create test case" >> {
      val read = new Read[User] {
        def apply(a: String): Either[String, User] = Right(User(a, 0))
      }

      val pr0 = provide[Int].static(0)

      check(Providers ~ ('a, pr0), Config("base", 0, "ref", 0), read, null, _ => HttpResponse("hello", 200, Map())) { implicit r => p =>
        val a = select('a, p).next(r)

        get(s"/uri/$a")
      } must beEqualTo(TestState(1, 0, 0)).awaitFor(1.second)

      checkAwait(Providers ~ ('a, pr0), Config("base", 0, "ref", 0), read, null, _ => HttpResponse("hello", 200, Map())) { implicit r => p =>
        val a = select('a, p).next(r)

        get(s"/uri/$a")
      }(1.second) === TestState(1, 0, 0)
    }

    "difference output (no test)" >> {
      import GenericDiffOps._

      val usr0 = User("foo", 1)
      val usr1 = User("bar", 2)

      val frd0 = Friends(usr0, usr1)
      val frd1 = Friends(usr1, usr0)

      val grp0 = Group(0L, Seq(usr0))
      val grp1 = Group(1L, Seq(usr1))

      val usrGrp0 = UserGroups(Map("a" -> grp0))
      val usrGrp1 = UserGroups(Map("a" -> grp1))

      def diff[A](l: A, r: A)(implicit d: GenericDiffRunner[A]): Seq[Diff] = d(l, r)

      printReasons(TestState(0, 0, 1, Seq(
        TotalDiff(diff(usr0, usr1)),
        TotalDiff(diff(frd0, frd1)),
        TotalDiff(diff(grp0, grp1)),
        TotalDiff(diff(grp0, Group(0L, Nil))),
        TotalDiff(diff(usrGrp0, usrGrp1)),
        TotalDiff(diff(usrGrp0, UserGroups(Map.empty))),
        TotalDiff(diff(ArrayOfUsers(0L, Array(usr0)), ArrayOfUsers(1L, Array(usr1)))),
        TotalDiff(diff(SetOfUsers(0L, Set(usr0)), SetOfUsers(1L, Set(usr1)))),
        TotalDiff(diff(Seq(usr0), Seq(usr1))),
        TotalDiff(diff(Map(0L -> usr0), Map(0L -> usr1)))
      )))

      implicit val userIg = IgnoreFields[User].ignore('age)

      printReasons(TestState(0, 0, 1, Seq(
        TotalDiff(diff(usr0, usr1))
      )))

      // force success
      1 === 1
    }
  }
}
