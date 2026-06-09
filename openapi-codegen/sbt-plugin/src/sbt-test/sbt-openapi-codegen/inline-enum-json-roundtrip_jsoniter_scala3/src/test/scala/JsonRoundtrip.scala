import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generated.TapirGeneratedEndpoints.*
import sttp.tapir.generated.TapirGeneratedEndpointsJsonSerdes.*

class JsonRoundtrip extends AnyFreeSpec with Matchers {
  "an inline enum nested in a json object can be round-tripped by generated jsoniter serdes" in {
    val json = """{"message":{"role":"assistant","content":"hi"}}"""
    val expected = ChatResponse(ChatResponseMessage(ChatResponseMessageRole.assistant, Some("hi")))

    // Fails on the current code: the codegen does not emit a JsonValueCodec for the inline enum
    // 'ChatResponseMessageRole', so jsoniter's derivation for ChatResponse treats the enum as a
    // discriminated ADT and throws "expected '{'" when decoding the bare string "assistant".
    readFromString[ChatResponse](json) shouldEqual expected
    readFromString[ChatResponse](writeToString(expected)) shouldEqual expected
  }
}
