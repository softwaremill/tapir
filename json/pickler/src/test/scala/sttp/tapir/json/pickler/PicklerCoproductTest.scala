package sttp.tapir.json.pickler

import magnolia1.SealedTrait
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Value
import sttp.tapir.Schema.annotations.{default, encodedName}
import sttp.tapir.{Schema, SchemaType}
import upickle.core.{ObjVisitor, Visitor}

import Fixtures.*

class PicklerCoproductTest extends AnyFlatSpec with Matchers {

  behavior of "Pickler derivation for coproducts"

  it should "handle a simple coproduct (no customizations)" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr1 = derived.toCodec.encode(MyCaseClass(ErrorTimeout, "msg18"))
    val jsonStr2 = derived.toCodec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18"))

    // then
    jsonStr1 shouldBe """{"fieldA":{"$type":"ErrorTimeout"},"fieldB":"msg18"}"""
    jsonStr2 shouldBe """{"fieldA":{"$type":"CustomError","msg":"customErrMsg"},"fieldB":"msg18"}"""
  }

  it should "apply custom field name encoding" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given PicklerConfiguration = PicklerConfiguration.default.withToEncodedName(toEncodedName = _.toUpperCase())
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)

    // when
    val derived = Pickler.derived[MyCaseClass]
    val jsonStr1 = derived.toCodec.encode(MyCaseClass(ErrorTimeout, "msg18"))
    val jsonStr2 = derived.toCodec.encode(MyCaseClass(CustomError("customErrMsg"), "msg18"))

    // then
    jsonStr1 shouldBe """{"FIELDA":{"$type":"ErrorTimeout"},"FIELDB":"msg18"}"""
    jsonStr2 shouldBe """{"FIELDA":{"$type":"CustomError","MSG":"customErrMsg"},"FIELDB":"msg18"}"""
  }

  it should "apply custom discriminator name" in {
    // given
    import generic.auto.* // for Pickler auto-derivation
    given PicklerConfiguration = PicklerConfiguration.default.withDiscriminator("kind")
    case class MyCaseClass(fieldA: ErrorCode, fieldB: String)
    val inputObj1 = MyCaseClass(CustomError("customErrMsg2"), "msg19")
    val inputObj2 = MyCaseClass(ErrorNotFound, "")

    // when
    val derived = Pickler.derived[MyCaseClass]
    val codec = derived.toCodec
    val jsonStr1 = codec.encode(inputObj1)
    val jsonStr2 = codec.encode(inputObj2)

    // then
    jsonStr1 shouldBe """{"fieldA":{"kind":"CustomError","msg":"customErrMsg2"},"fieldB":"msg19"}"""
    jsonStr2 shouldBe """{"fieldA":{"kind":"ErrorNotFound"},"fieldB":""}"""
    codec.decode(jsonStr1) shouldBe Value(inputObj1)
    codec.decode(jsonStr2) shouldBe Value(inputObj2)
  }

  it should "set discriminator value using class name" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val picklerResponse = Pickler.derived[StatusResponse]
    val inputObject = StatusResponse(StatusBadRequest(55))
    val codec = picklerResponse.toCodec
    val jsonStr = codec.encode(inputObject)
    val decoded = codec.decode(jsonStr)

    // then
    jsonStr shouldBe """{"status":{"$type":"StatusBadRequest","bF":55}}"""
    decoded shouldBe Value(inputObject)
  }

  it should "use custom discriminator name function" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    given PicklerConfiguration = PicklerConfiguration.default.withFullKebabCaseDiscriminatorValues
    val picklerResponse = Pickler.derived[StatusResponse]
    val inputObject = StatusResponse(StatusBadRequest(65))
    val codec = picklerResponse.toCodec
    val jsonStr = codec.encode(inputObject)
    val decoded = codec.decode(jsonStr)

    // then
    jsonStr shouldBe """{"status":{"$type":"sttp.tapir.json.pickler.fixtures.status-bad-request","bF":65}}"""
    decoded shouldBe Value(inputObject)
  }

  it should "set discriminator value using oneOfUsingField" in {
    // given
    val picklerOk = Pickler.derived[StatusOk]
    val picklerBadRequest = Pickler.derived[StatusBadRequest]
    val picklerInternalError = Pickler.derived[StatusInternalError.type]

    // when
    given statusPickler: Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, codeInt => s"code-$codeInt")(
      200 -> picklerOk,
      400 -> picklerBadRequest,
      500 -> picklerInternalError
    )
    val picklerResponse = Pickler.derived[StatusResponse]
    val codec = picklerResponse.toCodec
    val inputObject1 = StatusResponse(StatusBadRequest(54))
    val jsonStr1 = codec.encode(inputObject1)
    val decoded1 = codec.decode(jsonStr1)
    val inputObject2 = StatusResponse(StatusInternalError)
    val jsonStr2 = codec.encode(inputObject2)
    val decoded2 = codec.decode(jsonStr2)

    // then
    jsonStr1 shouldBe """{"status":{"$type":"code-400","bF":54}}"""
    decoded1 shouldBe Value(inputObject1)
    jsonStr2 shouldBe """{"status":{"$type":"code-500"}}"""
    decoded2 shouldBe Value(inputObject2)
  }

  it should "set discriminator value with oneOfUsingField for a deeper hierarchy" in {
    // given
    sealed trait Status:
      def code: Int

    sealed trait DeeperStatus extends Status
    sealed trait DeeperStatus2 extends Status

    case class StatusOk(oF: Int) extends DeeperStatus {
      def code = 200
    }
    case class StatusBadRequest(bF: Int) extends DeeperStatus2 {
      def code = 400
    }

    case class Response(status: Status)
    val picklerOk = Pickler.derived[StatusOk]
    val picklerBadRequest = Pickler.derived[StatusBadRequest]

    // when
    given statusPickler: Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, codeInt => s"code-$codeInt")(
      200 -> picklerOk,
      400 -> picklerBadRequest
    )
    val picklerResponse = Pickler.derived[Response]
    val inputObject = Response(StatusOk(818))
    val codec = picklerResponse.toCodec
    val encoded = codec.encode(inputObject)
    val decoded = codec.decode(encoded)

    // then
    encoded shouldBe """{"status":{"$type":"code-200","oF":818}}"""
    decoded shouldBe Value(inputObject)
  }

  it should "support sealed hierarchies looking like enums" in {
    // given
    import generic.auto.* // for Pickler auto-derivation

    // when
    val picklerResponse = Pickler.derived[Entity]
    val codec = picklerResponse.toCodec
    val inputObj = Entity.Business("221B Baker Street")
    val encoded = codec.encode(inputObj)

    // then
    encoded shouldBe """{"$type":"Business","address":"221B Baker Street"}"""
    codec.decode(encoded) shouldBe Value(inputObj)
  }
}
