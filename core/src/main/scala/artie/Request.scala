package artie

trait Request {

  type Params = Map[String, String]

  val Params = Map.empty[String, String]

  type Headers = Map[String, String]

  val Headers = Map.empty[String, String]

  implicit class ParamsAndHeadersOps(elements: Map[String, String]) {

    def <&>[A](key: String, value: A): Map[String, String] = elements + (key -> value.toString)
    def <&>[A](key: String, values: Seq[A]): Map[String, String] = elements + (key -> values.mkString(","))

    def <&>[A](key: String, valueO: Option[A]): Map[String, String] = valueO match {
      case Some(value) => <&>(key, value)
      case None        => elements
    }
  }

  sealed trait RequestType

  case object Get    extends RequestType
  case object Put    extends RequestType
  case object Post   extends RequestType
  case object Delete extends RequestType

  type RequestT = (RequestType, String, Params, Headers, Option[String])

  def show(request: RequestT): String = {
    val (_type, url, params, headers, contentO) = request

    val paramsStr  = params.map { case (k, v) => s"$k=$v" }.mkString("&")
    val headersStr = headers.map { case (k, v) => s"$k=$v" }.mkString(",")

    s"${_type.toString} $url" + {
      if (params.nonEmpty) "?" + paramsStr
      else                 ""
    } + {
      if (headers.nonEmpty) s" (headers = $headersStr)"
      else                  ""
    } + contentO.fold("")(c => s": $c")
  }

  final def get(uri: String, params: Params = Map.empty, headers: Headers = Headers): RequestT =
    (Get, uri, params, headers, None)

  final def put(uri: String, params: Params = Map.empty, headers: Headers = Headers, contentO: Option[String] = None): RequestT =
    (Put, uri, params, headers, contentO)

  final def post(uri: String, params: Params = Map.empty, headers: Headers = Headers, contentO: Option[String] = None): RequestT =
    (Post, uri, params, headers, contentO)

  final def delete(uri: String, params: Params = Map.empty, headers: Headers = Headers): RequestT =
    (Delete, uri, params, headers, None)
}
