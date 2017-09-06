package artie

trait Request {

  type Params = Map[String, String]

  val params = Map.empty[String, String]

  implicit class ParamsOps(params: Params) {

    def <&>[A](key: String, value: A): Params = params + (key -> value.toString)
    def <&>[A](key: String, values: Seq[A]): Params = params + (key -> values.mkString(","))

    def <&>[A](key: String, valueO: Option[A]): Params = valueO match {
      case Some(value) => <&>(key, value)
      case None        => params
    }
  }

  sealed trait RequestType

  case object Get    extends RequestType
  case object Put    extends RequestType
  case object Post   extends RequestType
  case object Delete extends RequestType

  type RequestT = (RequestType, String, Params, Option[String])

  final def get(uri: String, params: Params = Map.empty): RequestT =
    (Get, uri, params, None)

  final def put(uri: String, params: Params = Map.empty, contentO: Option[String] = None): RequestT =
    (Put, uri, params, contentO)

  final def post(uri: String, params: Params = Map.empty, contentO: Option[String] = None): RequestT =
    (Post, uri, params, contentO)

  final def delete(uri: String, params: Params = Map.empty): RequestT =
    (Delete, uri, params, None)
}
