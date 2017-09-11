package artie

import artie.implicits._

import org.specs2.mutable.Specification

final case class User(name: String, age: Int)
final case class Friends(base: User, friend: User)
final case class Group(id: Long, users: Seq[User])
final case class UserGroups(groups: Map[String, Group])
final case class UserGroupsId(groups: Map[Int, Group])
final case class ArrayOfUsers(id: Long, users: Array[User])
final case class SetOfUsers(id: Long, users: Set[User])

final class GenericDiffSpec extends Specification {

  import artie.GenericDiffOps._

  val usr0 = User("foo", 1)
  val usr1 = User("bar", 2)
  val usr2 = User("bar", 3)

  "Generic difference calculation of case classes" >> {
    "simple case class" >> {
      diff(usr0, usr0) === Nil
      diff(usr0, usr1) === Seq(FieldDiff("name", "foo", "bar"), FieldDiff("age", 1, 2))
    }

    "nested case classes" >> {
      diff(Friends(usr0, usr1), Friends(usr0, usr1)) === Nil
      diff(Friends(usr0, usr1), Friends(usr0, usr2)) === Seq(
        ClassDiff("friend", Seq(FieldDiff("age", 2, 3)))
      )
    }

    "case class specific comparision" >> {
      implicit val friendsComp = new Compare[Friends] {
        def compare(l: Friends, r: Friends): Seq[Option[FieldDiff]] = Seq(
          diff("base")(l.base, r.base)
        )
      }

      diff(Friends(usr0, usr1), Friends(usr0, usr2)) === Nil
    }

    "nested case classes with sequences" >> {
      diff(Group(0L, Nil), Group(0L, Nil)) === Nil
      diff(Group(0L, Seq(usr0, usr1)), Group(0L, Seq(usr0, usr1))) === Nil
      diff(Group(0L, Seq(usr0, usr1)), Group(0L, Seq(usr0, usr2))) === Seq(
        CollectionElementsDiff("users", Seq(TotalDiff(Seq(FieldDiff("age", 2, 3)))))
      )
      diff(Group(0L, Seq(usr0, usr1)), Group(0L, Seq(usr1, usr0))) === Seq(
        CollectionElementsDiff("users", Seq(
          TotalDiff(Seq(
            FieldDiff("name", "foo", "bar"),
            FieldDiff("age", 1, 2)
          )),
          TotalDiff(Seq(
            FieldDiff("name", "bar", "foo"),
            FieldDiff("age", 2, 1)
          ))
        ))
      )
      diff(Group(0L, Seq(usr0)), Group(0L, Seq(usr0, usr1))) === Seq(
        CollectionSizeDiff("users", 1, 2),
        CollectionElementsDiff("users", Seq(
          MissingValue(usr1)
        ))
      )
    }

    "nested case classes with arrays" >> {
      diff(ArrayOfUsers(0L, Array(usr0, usr1)), ArrayOfUsers(0L, Array(usr0, usr1))) === Nil
      diff(ArrayOfUsers(0L, Array(usr0, usr1)), ArrayOfUsers(0L, Array(usr1, usr0))) === Seq(
        CollectionElementsDiff("users", Seq(
          TotalDiff(Seq(
            FieldDiff("name", "foo", "bar"),
            FieldDiff("age", 1, 2)
          )),
          TotalDiff(Seq(
            FieldDiff("name", "bar", "foo"),
            FieldDiff("age", 2, 1)
          ))
        ))
      )
    }

    "nested case classes with sets" >> {
      diff(SetOfUsers(0L, Set(usr0, usr1)), SetOfUsers(0L, Set(usr1, usr0))) === Nil
      diff(SetOfUsers(0L, Set(usr0, usr2)), SetOfUsers(0L, Set(usr1, usr0))) === Seq(
        CollectionElementsDiff("users", Seq(
          MissingValue(usr2),
          MissingValue(usr1)
        ))
      )
    }

    "nested case classes with maps" >> {
      diff(UserGroups(Map.empty), UserGroups(Map.empty)) === Nil
      diff(UserGroups(Map("a" -> Group(0L, Seq(usr0, usr1)))), UserGroups(Map("a" -> Group(0L, Seq(usr0, usr1))))) === Nil
      diff(UserGroupsId(Map(1 -> Group(0L, Seq(usr0, usr1)))), UserGroupsId(Map(1 -> Group(0L, Seq(usr0, usr1))))) === Nil
      diff(UserGroups(Map("a" -> Group(0L, Seq(usr0, usr1)))), UserGroups(Map("a" -> Group(0L, Seq(usr0, usr2))))) === Seq(
        MapDiff("groups", Seq(
          "a" -> TotalDiff(Seq(CollectionElementsDiff("users", Seq(TotalDiff(Seq(FieldDiff("age", 2, 3)))))))
        ))
      )
      diff(UserGroups(Map("a" -> Group(0L, Seq(usr0)))), UserGroups(Map.empty)) === Seq(
        CollectionSizeDiff("groups", 1, 0),
        MapDiff("groups", Seq(
          "a" -> MissingValue(Group(0L, Seq(usr0)))
        ))
      )
    }
  }
}
