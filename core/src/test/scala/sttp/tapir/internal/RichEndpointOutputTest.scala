package sttp.tapir.internal

import org.scalatest.{FlatSpec, Matchers}
import sttp.model.StatusCode
import sttp.tapir.CodecForOptional.PlainCodecForOptional
import sttp.tapir._
import sttp.tapir.internal.ErrorInfo._

import scala.collection.immutable.ListMap

class RichEndpointOutputTest extends FlatSpec with Matchers {
  type BasicOutputs = Vector[EndpointOutput.Basic[_]]

  def test(desc: String, e: (EndpointOutput[_], ListMap[Option[StatusCode], BasicOutputs])): Unit = {
    "RichEndpointOutput#asBasicOutputsMap" should desc in {
      val (output, expected) = e
      val outputMap = output.asBasicOutputsMap
      outputMap shouldBe expected
    }
  }

  val endpointOutput: (
      EndpointOutput.OneOf[ErrorInfo],
      ListMap[Option[StatusCode], Vector[EndpointIO.Body[_, CodecFormat.TextPlain, _]]]
  ) = {
    val unknown: EndpointIO.Body[Unknown, CodecFormat.TextPlain, String] =
      EndpointIO.Body(codecForOptionalUnknown, EndpointIO.Info.empty).description("Bad request 1.")
    val unauthorized: EndpointIO.Body[Unauthorized, CodecFormat.TextPlain, _] =
      plainBody[Unauthorized].description("Bad request 2.")
    val notFound: EndpointIO.Body[NotFound, CodecFormat.TextPlain, _] = plainBody[NotFound].description("Not found")
    val endpointOut =
      sttp.tapir.oneOf[ErrorInfo](
        sttp.tapir.statusMapping(StatusCode.BadRequest, unknown),
        sttp.tapir.statusMapping(StatusCode.BadRequest, unauthorized),
        sttp.tapir.statusMapping(StatusCode.NotFound, notFound)
      )
    println(endpoint.in("asd").out(endpointOut).show)
    val output =
      ListMap(Option(StatusCode.BadRequest) -> Vector(unknown, unauthorized), Option(StatusCode.NotFound) -> Vector(notFound))
    endpointOut -> output
  }

  test("collect endpoint bodies by status code", endpointOutput)

}

sealed trait ErrorInfo extends Product with Serializable
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo
object ErrorInfo {
  implicit val codecForOptionalUnknown: PlainCodecForOptional[Unknown] =
    CodecForOptional.fromCodec(Codec.plainCodec[Unknown](Unknown(1, _)))
  implicit val codecForOptionalNotFound: PlainCodecForOptional[NotFound] =
    CodecForOptional.fromCodec(Codec.plainCodec[NotFound](NotFound))
  implicit val codecForOptionalUnauthorized: PlainCodecForOptional[Unauthorized] =
    CodecForOptional.fromCodec(Codec.plainCodec[Unauthorized](Unauthorized))
}
