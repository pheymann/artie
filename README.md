[![Build Status](https://travis-ci.org/pheymann/artie.svg?branch=master)](https://travis-ci.org/pheymann/artie)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.pheymann/artie_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.pheymann/artie_2.12)
[![codecov.io](http://codecov.io/github/pheymann/artie/coverage.svg?branch=master)](http://codecov.io/github/pheymann/artie?branch=master)

# [WIP] artie {from rrt := rest-refactoring-test-framework}
You want to change a (legacy) REST service which has no tests and it is impossible to
write some tests without rebuilding the whole thing? If so this tool may help you. It is
a small framework to generate REST request from different data sets, run them against
two instances of your service (old and new) and compare the responses.

The only thing you have to do is:

```Scala
import artie._
import artie.implicits._

// write a refactoring spec
object MyServiceRefactoring extends RefactoringSpec("my-service") {

  import DatabaseGenerator.DatabaseConfig

  // give some informations
  val conf = Config("old-host", 8080, "new-host", 8080)
  val db   = mysql("db-host", "user", "pwd")

  // add some data
  val providers = Providers ~
    // random ages between 10 and 100
    ('ages, provide[Int].random(10, 100) ~

    // some user ids from a db
    ('userIds, provide[Long].database.random("users_table", "user_id", limit = 100, db)

  // you have to provide `read` (see below)
  check("get-user", providers, conf, read[User]) { implicit r => p =>
    val userId = select('userIds, p).next
    val ageO   = select('ages, p).nextOpt

    // request builder
    get(s"/user/$userId", Params <&> ("age", ageO))
  }
}
```

And run it:

```
# if both instances behave the same
sbt "runMain MyServiceRefactoring"

testing refactorings for my-service:
  + check get-user

Success: Total: 1; Succeeded: 1, Invalid: 0; Failed: 0

# in presence of differences
sbt "it:runMain MyServiceRefactoring"

testing refactorings for my-service:
  + check get-user
    processed: 1 / 1
    
    Get /user/0?age=20
    {
      city: "Hamburg" != "New York"
    }

Failed: Total: 1; Succeeded: 0, Invalid: 0; Failed: 1
```

Here `Invalid` indicates response pairs with the same error code (3.x.x, 4.x.x or 5.x.x). Invalide results
don't fail a test.

For some examples take a look [here](https://github.com/pheymann/artie/tree/master/examples/src/it/scala/examples).

## Table of Contents
 - [Get This Framework](#get-this-framework)
 - [Dependencies](#dependencies)
 - [Documentation](#documentation)

### Get This Framework
You can add it as dependency for Scala **2.11** and **2.12**:

```Scala
// take a look at the maven batch to find the latest version
libraryDependencies += "com.github.pheymann" %% "artie" % <version> % Test
```

or build it locally:

```
git clone https://github.com/pheymann/artie.git
cd artie
sbt "publishLocal"
```

### Dependencies
I tried to keep the dependencies to external libraries as small as possible. Currently this framework uses:
  - [shapeless](https://github.com/milessabin/shapeless/)
  - [scalaj-http](https://github.com/scalaj/scalaj-http/)

## Documentation
In the following I'll describe the basic elements of a refactoring spec: test configuration (`TestConfig`), data providers (`Provider`) and multiple test cases (`check`), in more detail.

### Table of Contents
 - [TestConfig](#testconfig)
 - [Providers](#providers)
 - [Read responses](#read-responses)
 - [Data Selector](#data-selector)
 - [Request Builder](#request-builder)
 - [Test Suite](#test-suite)
 - [Ignore Response Fields](#ignore-response-fields)
 - [Response Comparison](#response-comparison)
 - [Add your Database](#add-your-database)

### TestConfig
Configuration for test execution and *rest* calls:

```Scala
Config("base-host", 80, "ref-host", 80)
  .repetitions(100)
  .parallelism(3)
  .stopOnFailure(false)
  .shownDiffsLimit(10)
```

**Mandatory**:
 - `baseHost`: host address of the old/original service
 - `basePort`: port of the old/original service
 - `refactoredHost`: host address of the new/refactored service
 - `refactoredPort`: port of the new/refactored service

**Additional settings**:
 - `repetitions`: [default = 1] how many requests will be created (repeat this check)
 - `parallelism`: [default = 1] how many requests can be ran in parallel
 - `stopOnFailure`: [default = true] if set to `false` the test will continue in the presence of a difference
 - `shownDiffsLimit`: [default = 1] how many diffs are shown

### Providers
Providers provide a collection of elements of some type `A` for later usage with [data selectors](#data-selector). They have to be tagged (with `Symbol`s) when passed to a test case:

```Scala
val providers = Providers ~ ('tag0, prov0) ~ ('tag1, prov1)
```

#### Static
Provides data from a static sequence:

```Scala
provide[User].static(User("foo"), User("bar"))
```

#### Random
Provides data from a random generator in a range of `min` / `max`:

```Scala
provide[Long].random(0, 100)
```

#### Database
Provides data from a Database query:

```Scala
// provide a query (add a LIMIT as all data is eagerly loaded)!
provide[Long].database("select id from users limit 100", db)

// or randomly select 100 elements
provide[Long].database.random("users", "id", 100, db)
```

##### Database
Currently **artie** provides you with `mysql` and `h2` which can be used like this:

```Scala
val db0 = mysql(<host>, <user>, <password>)
val db1 = h2(<host>, <user>, <password>)
```

### Read REST responses
Now that we have described config and providers we still need the Json mapping. This is done by creating an instance of `Read` for your response type:

```Scala
// manually for every type
val userRead = new Read[User] {
  def apply(json: String): Either[String, User] = ???
}

// or by reusing your mappings from some json-frameworks
object PlayJsonToRead {

  def read[U](implicit reads: play.json.Reads[U]): Read[U] = new Read[U] {
    def apply(json: String): Either[String, U] = 
      Json.fromJson[U](Json.parse(json)) match {
        case JsSuccess(u, _) => Right(u)
        case JsError(errors) => Left(errors.mkString("\n"))
      }
  }
}
```

Now we can build a test case by calling `check`:

```Scala
// r := Random instance
// p := list of all our tagged providers
check("my endpoint", providers, config, read[User]) { implicit r => p =>
  ???
}
```

But wait, how do we get data out of our provider instance `p` to build requests?

### Data Selector
You can select data from some provider `'tag` as shown below:

```Scala
implicit r => p => 
  select('tag, p).next // single element
  select('tag, p).nextOpt // single element which can be `Some` or `None`
  select('tag, p).nextSeq(10) // sequence of elements of length 10
  select('tag, p).nextSet(10) // set of elements of maximum size 10
```

If you try to access a provider which isn't part of `p` the compile will tell you.

### Request Builder
You can create:
 - *get*
 - *put*
 - *post*
 - *delete*

requests by calling:

```Scala
get("http://my.service/test")

post("http://my.service/test", contentO = Some("""{"id":0}"""))
```

#### Query Parameter and Headers
If you need query parameters or headers use:

```Scala
val p = Params <&> ("a", 1) <&> ("b", Some(0)) <&> ("c", Seq(1, 2, 3))
val h = Headers <&> ("Content-Type", "application/json")

get("http://my.service/test", params = p, headers = h)
```

### Test Suite
You don't want to execute all your specs by hand? Then add a `RefactoringSuite`:

```Scala
object MySuite extends RefactoringSuite {
  
  val specs = FirstSpec :: SecondSpec :: Nil
}
```

This will execute all your `RefactoringSpec`s you add to `specs` in sequence.

### Ignore Response Fields
Sometimes it is necessary to ignore some response fields (eg. timestamp). If you don't want to rewrite your
json mapping you can provide a `IgnoreFields` instance:

```
final case class Log(msg: String, time: Long)

implicit val logIgnore = IgnoreFields[Log].ignore('time)
   
check("log-endpoint", providers, conf, read[Log]) { ...}
```

The `Symbol` has to be equal to the field name. If you write something which doesn't exists in your `case class`
the compiler will tell you.

### Response Comparison
Response comparison is done by creating a list of field-value pairs ([LabelledGenerics](https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#generic-representation-of-sealed-families-of-case-classes)) from your responses of class `R` 
and comparing each field:

```Scala
(old: R, refact: R) => (old: FieldValues, refact: FieldValues) => Seq[Diff]
```

The result is a sequence of `Diff` with each diff providing the field name and:
  - the two original values,
  - a set of diffs of the two values.

Currently the framework is able to provide detailed compare-results (fields with values) for:
  - simple case classes (`case class User(id: Long, name: String`)
  - nested case classes (`case class Friendship(base: User, friend: User)`)
  - sequences, sets and arrays of case classes (`case class Group(members: Set[User])`)
  - maps of case classes (`case class UserTopic(topics: Map[Topic, User])`)
  - combination of these

Everythings else will be compare by `!=` and completely reported on failure.

If you need something else take a look [here](https://github.com/pheymann/artie/blob/master/core/src/main/scala/artie/GenericDiff.scala#L90) to get an idea how to implement it.

### Add your Database
You can add your Database as easy as this:

```Scala
trait Mysql extends Database {

  val driver = "com.mysql.jdbc.Driver"

  def qualifiedHost = "jdbc:mysql://" + host

  def randomQuery(table: String, column: String, limit: Int): String =
    s"""SELECT DISTINCT t.$column
       |FROM $table AS t
       |ORDER BY RAND()
       |LIMIT $limit
       |""".stripMargin  
}

object Mysql {

  def apply(_host: String, _user: String, _password: String) = new Mysql {
    val host = _host
    val user = _user
    val password = _password
  }
}
```
