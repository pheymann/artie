package util

import artie.Read

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import akka.stream.ActorMaterializer
import play.api.libs.json._
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.io.StdIn
import scala.util.control.NonFatal

object PlayJsonToRead {

  def read[U](implicit reads: Reads[U]): Read[U] = new Read[U] {
    def apply(json: String): Either[String, U] = 
      Json.fromJson[U](Json.parse(json)) match {
        case JsSuccess(u, _) => Right(u)
        case JsError(errors) => Left(errors.mkString("\n"))
      }
  }
}

final case class User(id: Int, name: String)

object User {
  implicit val userFormat  = Json.format[User]
}

final case class Group(id: Long, users: Seq[User])

object Group {
  implicit val groupFormat = Json.format[Group]
}

final case class TopicToUser(topics: Map[String, User])

object TopicToUser {
  implicit val topicFormat = Json.format[TopicToUser]
}

abstract class ServerRoutes {

  import PlayJsonSupport._
  import Directives._

  def name: String

  def statusCode: StatusCode

  def getResponse(id: Int): User
  def putResponse(id: Int, content: Seq[User]): Group
  def postResponse(id: Int, content: User): TopicToUser
  def deleteResponse(id: Int): User

  val excepHandler = ExceptionHandler {
    case NonFatal(cause) =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        cause.printStackTrace()
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "something went wrong here!!!"))
      }
  }

  val routes = handleExceptions(excepHandler) {
    path("user" / IntNumber) { id =>
      Directives.get {
        complete((statusCode, getResponse(id)))
      } ~
      (Directives.put & entity(as[Seq[User]])) { content =>
        complete((statusCode, putResponse(id, content)))
      } ~
      (Directives.post & entity(as[User])) { content =>
        complete((statusCode, postResponse(id, content)))
      } ~
      Directives.delete {
        complete((statusCode, deleteResponse(id)))
      }
    }
  }

  def port: Int

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(name)

    import system.dispatcher

    implicit val materializer = ActorMaterializer()

    println(s"start TestServer $name")
    val serverF = Http().bindAndHandle(routes, "localhost", port)

    StdIn.readLine("Press ENTER to stop ...")

    serverF.foreach { binding =>
      binding.unbind()
      system.terminate()
      println(s"stopped TestServer $name")
    }
  }
}

object SuccessfulServerA extends ServerRoutes {

  override val name = "success-a"
  override val port = 9000

  override val statusCode = StatusCodes.OK

  override def getResponse(id: Int): User = User(id, "Bob")
  override def putResponse(id: Int, content: Seq[User]): Group =
    Group(0L, content)
  override def postResponse(id: Int, content: User): TopicToUser =
    TopicToUser(Map("mine" -> User(id, "Bob"), "other" -> content))
  override def deleteResponse(id: Int): User = User(id, "Bob")
}

object SuccessfulServerB extends ServerRoutes {

  override val name = "success-b"
  override val port = 9001

  override val statusCode = StatusCodes.OK

  override def getResponse(id: Int): User = User(id, "Joe")
  override def putResponse(id: Int, content: Seq[User]): Group =
    Group(0L, User(id, "Jimmy") +: content.tail )
  override def postResponse(id: Int, content: User): TopicToUser =
    TopicToUser(Map("mine" -> User(id, "Joe"), "other" -> content))
  override def deleteResponse(id: Int): User = User(id, "Joe")
}

object FailingServer extends ServerRoutes {

  override val name = "failed"
  override val port = 9002

  override val statusCode = StatusCodes.BadRequest

  override def getResponse(id: Int): User = User(id, "Bob")
  override def putResponse(id: Int, content: Seq[User]): Group =
    Group(0L, content)
  override def postResponse(id: Int, content: User): TopicToUser =
    TopicToUser(Map("mine" -> User(id, "Bob"), "other" -> content))
  override def deleteResponse(id: Int): User = User(id, "Bob")
}
