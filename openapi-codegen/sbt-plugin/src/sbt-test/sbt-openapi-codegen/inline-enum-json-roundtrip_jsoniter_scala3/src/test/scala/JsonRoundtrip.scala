import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generated.TapirGeneratedEndpoints.*
import sttp.tapir.generated.TapirGeneratedEndpointsJsonSerdes.*

class JsonRoundtrip extends AnyFreeSpec with Matchers {
  // Without a dedicated codec for an inline enum, jsoniter derives it as a discriminated ADT and throws
  // "expected '{'" on the bare string value. We exercise inline enums reached as a direct property, an array
  // element, a map value, and through a single-element allOf, asserting each round-trips its bare-string values.
  "inline enums nested in a json object (direct, array, map, allOf) can be round-tripped by generated jsoniter serdes" in {
    val json =
      """{"message":{"role":"assistant","roles":["user","tool"],"labels":{"a":"system"},"priority":"user","content":"hi"}}"""
    val expected = ChatResponse(
      ChatResponseMessage(
        role = ChatResponseMessageRole.assistant,
        roles = Seq(ChatResponseMessageRolesItem.user, ChatResponseMessageRolesItem.tool),
        labels = Map("a" -> ChatResponseMessageLabelsItem.system),
        priority = ChatResponseMessagePriority.user,
        content = Some("hi")
      )
    )

    readFromString[ChatResponse](json) shouldEqual expected
    readFromString[ChatResponse](writeToString(expected)) shouldEqual expected
  }
}
