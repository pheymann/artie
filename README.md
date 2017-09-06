# [WIP] artie {from rrt := rest-refactoring-test-framework}
You want to change a (legacy) REST service which has not tests and it is impossible to
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
  val conf = config("old-host", 8080, "new-host", 8080)
  val dbConf = DatabaseConfig(mysql, "db-host", "user", "pwd")

  // add some data
  val providers = Providers ~
    // random ages between 10 and 100
    ('ages, provide[Int].random(10, 100) ~

    // some user ids from a db
    ('userIds, provide[Long].database.random("users_table", "user_id", limit = 100, dbConf)

  // you have to provide `read` (see below)
  check("get-user", providers, conf, read[User]) { implicit r => p =>
    val userId = select('userIds, p).next
    val ageO   = select('ages, p).nextOpt

    // request builder
    get(s"/user/$userId", params <&> ("age", ageO))
  }
}
```

And run it:

```
# if both instances behave the same
sbt "it:test"

testing refactorings for my-service:
  + check get-user

Success: Total: 1; Succeeded: 1, Invalid: 0; Failed: 0

# in presence of differences
sbt "it:test"

testing refactorings for my-service:
  + check get-user
    failed with:

    {
      age: 10 != 20
    }

Failed: Total: 1; Succeeded: 0, Invalid: 0; Failed: 1
```

For some examples take a look into the [integration tests](https://github.com/pheymann/artie/tree/master/core/src/it/scala/artie) or [examples](https://github.com/pheymann/artie/tree/master/examples/src/it/scala/single).

## Table of Contents
 - [Get This Framework](#get-this-framework)
 - [Read responses](#read-responses)
 - [Providers](#providers)
 - [TestConfig](#testconfig)
 - [Request Builder](#request-builder)
 - [Add your Database](#add-your-database)

### Get This Framework
As this is still WIP you can only clone and build it:

```
git clone https://github.com/pheymann/artie.git
cd artie
sbt "publishLocal"
```

But I will push it to Maven as fast as possible.

### Read responses
You have to provide functions mapping raw json strings to your `case class` instances.
They are called `Read`s and implemented like this:

```Scala
object PlayJsonToRead {

  def read[U](implicit reads: Reads[U]): Read[U] = new Read[U] {
    def apply(json: String): Either[String, U] = 
      Json.fromJson[U](Json.parse(json)) match {
        case JsSuccess(u, _) => Right(u)
        case JsError(errors) => Left(errors.mkString("\n"))
      }
  }
}
```

### Providers
Providers select a single elements randomly on every `next` from an underlying
data set.

To select the next element you have to determine the provide by is **id**:

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
provide[Long].database("select id from users limit 100", dbConfig)

// or randomly select elements
provide[Long].database.random("users", "id", 100, dbConfig)
```

##### Database Configuration
 - `database`: an instance of [Database](), currently only `mysql` but can easily created for other DBs
 - `host`: host machine of the DB
 - `user`: database user
 - `password`: database user password

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
get(<url>, <query-params>)

post((<url>, <query-params>, Some(<json-string>))
```

#### Query Parameter
If you need query params use:

```Scala
val p = params <&> ("a", 1) <&> ("b", Some(0)) <&> ("c", Seq(1, 2, 3))

get(???, p)
```

### Add your Database
You can add your Database as easy that:

```Scala
object mysql extends Database {

  val driver = "com.mysql.jdbc.Driver"

  def qualifiedHost(host: String) = "jdbc:mysql://"

  def randomQuery(table: String, column: String, limit: Int): String =
    s"""SELECT DISTINCT t.$column
       |FROM $table AS t
       |ORDER BY RAND()
       |LIMIT $limit
       |""".stripMargin  
}
```
