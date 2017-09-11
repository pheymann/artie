[![Build Status](https://travis-ci.org/pheymann/artie.svg?branch=master)](https://travis-ci.org/pheymann/artie)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.pheymann/artie_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.pheymann/artie_2.12)

# [WIP] artie {from rrt := rest-refactoring-test-framework}
You want to change a (legacy) REST service which has no tests and it is impossible to
write some without rebuilding the whole thing? If so this tool may help you. It is
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
sbt "it:runMain MyServiceRefactoring"

testing refactorings for my-service:
  + check get-user

Success: Total: 1; Succeeded: 1, Invalid: 0; Failed: 0

# in presence of differences
sbt "it:runMain MyServiceRefactoring"

testing refactorings for my-service:
  + check get-user
    failed with:

    {
      age: 10 != 20
    }

Failed: Total: 1; Succeeded: 0, Invalid: 0; Failed: 1
```

For some examples take a look [here](https://github.com/pheymann/artie/tree/master/examples/src/it/scala/examples).

## Table of Contents
 - [Get This Framework](#get-this-framework)
 - [Dependencies](#dependencies)
 - [Read responses](#read-responses)
 - [Response Comparison](#response-comparison)
 - [Providers](#providers)
 - [TestConfig](#testconfig)
 - [Request Builder](#request-builder)
 - [Add your Database](#add-your-database)
 - [Ignore Response Fields](#ignore-response-fields)

### Get This Framework
You can add it as dependency for Scala **2.11** and **2.12**:

```Scala
libraryDependencies += "com.github.pheymann" %% "artie" % "0.1.0-RC1" % Test
```

or build it locally:

```
git clone https://github.com/pheymann/artie.git
cd artie
sbt "publishLocal"
```

In **Master** you will find the build for Scala 2.12.x. If you need 2.11.x checkout branch [2.11.x](https://github.com/pheymann/artie/tree/2.11.x).

### Dependencies
I tried to keep the dependencies to external libraries as small as possible. Currently this framework uses:
  - [shapeless](https://github.com/milessabin/shapeless/)
  - [scalaj-http](https://github.com/scalaj/scalaj-http/)

### Read responses
You have to provide functions mapping raw json strings to your `case class` instances.
They are called `Read`s and implemented like this:

```Scala
// by hand
val userRead = new Read[User] {
  def apply(json: String): Either[String, User] = ???
}

// or by reusing your mappings from some frameworks
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

### Response Comparison
Response comparison is done by creating a list of field-value pairs ([LabelledGenerics](https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#generic-representation-of-sealed-families-of-case-classes)) from your responses of class `R` 
and comparing each field:

```Scala
(old: R, refact: R) => (old: FieldValues, refact: FieldValues) => Seq[Diff]
```

The result is a sequence of `Diff` with each diff providing the field name and:
  - the two original values,
  - a set of diffs of the two values.

Currently the framework is able to provide detailed compare-results for:
  - simple case classes
  - nested case classes
  - sequences of primitives or case classes
  - maps of primitives or case classes

Everythings else will be compare by `!=` and completely reported on failure.

If you need something else take a look [here](https://github.com/pheymann/artie/blob/master/core/src/main/scala/artie/GenericDiff.scala#L90) to get an idea how to implement it.

### Providers
Providers select a single element randomly on every `next` from an underlying
data set.

To select the next element you have to determine the provide by its **id**:

```Scala
// for a scalar value
val id = select('userIds, providers).next

// for an Option; maybe a Some maybe a None
val idO = select('userIds, providers).nextOpt
```

This **id** based select is typesafe thanks to [shapeless](https://github.com/milessabin/shapeless). 
This means your compiler will tell you if you try to select an non-existing provider.

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

// or randomly select elements
provide[Long].database.random("users", "id", 100, db)
```

##### Database
Currently provided are `mysql` and `h2` and have to be used like this:

```Scala
val db0 = mysql(<host>, <user>, <password>)
val db1 = h2(<host>, <user>, <password>)
```

### TestConfig
Mandatory informations:
 - `baseHost`: host address of the old service
 - `basePort`: port of the old service
 - `refactoredHost`: host address of the new (refactored) service
 - `refactoredPort`: port of the new (refactored) service

Additional settings (function calls):
 - `repetitions`: how many requests will be created (repeat this check)
 - `parallelism`: how many requests can be ran in parallel
 - `stopOnFailure`: default is `true`, if set to `false` the test will continue in the presence of a difference
 - `shownDiffsLimit`: default is `1`, how many diffs are shown

### Request Builder
You can create:
 - *get*
 - *put*
 - *post*
 - *delete*

requests by calling:

```Scala
get(<url>, <query-params>, <headers>)

post((<url>, <query-params>, <headers>, Some(<json-string>))
```

#### Query Parameter and Headers
If you need query params or headers use:

```Scala
val p = Params <&> ("a", 1) <&> ("b", Some(0)) <&> ("c", Seq(1, 2, 3))
val h = Headers <&> ("Content-Type", "application/json")

post(???, p, h, Some("""{"id": 0}"""))
```

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
### Ignore Response Fields
Sometimes it is necessary to ignore some response fields (eg. timestamp). If you don't want to rewrite your
json mapping you can provide a `Compare` instance:

```
final case class Log(msg: String, time: Long)

implicit val logComp = new Compare[Log] {
  def compare(l: Log, r: Log) = Seq(
    diff("msg")(l.msg, r.msg)
  }
}
   
check("log-endpoint", providers, conf, read[Log]) { ...}
```
