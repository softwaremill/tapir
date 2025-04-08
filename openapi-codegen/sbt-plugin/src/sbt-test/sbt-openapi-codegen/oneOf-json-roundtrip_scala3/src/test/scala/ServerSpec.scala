import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generated.TapirGeneratedEndpoints._

class ServerSpec extends AnyFreeSpec with Matchers {

  "can construct uri with default values" in {
    Servers.`https://{environment}.my-co.org:{port}/api/{customer}/prefix`.uri().toString() shouldEqual
      "https://prod.my-co.org:1234/api/big-dogs/prefix"
  }
}
