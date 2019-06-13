package tapir.client.generated

trait HttpCaller[L] {
  def imports: String
  def httpCall(httpCall: HttpCall[_, _, _], queryParameters: Map[String, String]): String
}
