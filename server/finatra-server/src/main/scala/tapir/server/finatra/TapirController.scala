package tapir.server.finatra
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import tapir.Endpoint
import tapir.internal._
import tapir.internal.server.DecodeInputs

trait TapirController { self: Controller =>
  implicit class RichFinatraEndpoint[I, E, O, F[_]](e: Endpoint[I, E, O, _]) {
    def toRoute(logic: I => F[Either[E, O]]): Unit = {

      any(e.input.path) { request: Request =>
        DecodeInputs(e.input, new FinatraDecodeInputsContext(request))



      }
    }
  }

}
