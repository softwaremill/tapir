package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.data.Fruit

object OneOfBody {
  implicit val fruitXmlCodec: Codec[String, Fruit, CodecFormat.Xml] =
    Codec
      .id(CodecFormat.Xml(), Schema.string[String])
      .mapDecode { xml =>
        DecodeResult.fromOption("""<f>(.*?)</f>""".r.findFirstMatchIn(xml).map(_.group(1)).map(Fruit))
      }(fruit => s"<f>${fruit.f}</f>")

  val in_one_of_json_xml_text_out_string: PublicEndpoint[Fruit, Unit, String, Any] = endpoint.post
    .in(
      oneOfBody(
        jsonBody[Fruit],
        xmlBody[Fruit],
        stringBody.map(Fruit)(_.f)
      )
    )
    .out(stringBody)

  val in_string_out_one_of_json_xml_text: PublicEndpoint[String, Unit, Fruit, Any] = endpoint.post
    .in(stringBody)
    .out(
      oneOfBody(
        jsonBody[Fruit],
        xmlBody[Fruit],
        stringBody.map(Fruit)(_.f)
      )
    )
}
