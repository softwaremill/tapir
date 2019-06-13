package tapir.client.generated.language.scala
import tapir.client.generated.{HttpCall, HttpCaller}
import tapir.client.generated.language.Scala

case object Sttp extends HttpCaller[Scala] {
  override def imports: String =
    "import com.softwaremill.sttp._"

  override def httpCall(httpCall: HttpCall[_, _, _], queryParameters: Map[String, String]): String = {
    val queryParamsString = queryParameters
      .map {
        case (query, param) =>
          s"$query=$param"
      }
      .reduceOption(_ + "&" + _)
      .map("?" + _)
      .getOrElse("")

    val callParamsString = httpCall.payloadType
      .map(typeDescription => s"${typeDescription.name.toLowerCase}: ${typeDescription.name}")
      .getOrElse("")

    s"""def ${httpCall.method}($callParamsString) =
       |  sttp.${httpCall.method.toLowerCase}(uri"$$previousPath$queryParamsString")
       |""".stripMargin
  }
}
