package tapir.server.finatra
import com.twitter.finatra.http.Controller
import tapir.Endpoint
import tapir.internal.server.DecodeInputs
import tapir.internal._
import tapir.model.Method

trait TapirController { self: Controller =>
  implicit class RichFinatraEndpoint[I, E, O, F[_]](e: Endpoint[I, E, O, _) {
    def toRoute(logic: I => F[Either[E, O]]): Unit = {


      val basicInputs = e.input.asVectorOfSingleInputs
      val method: Method = ???
      method match {
        case Method.Get =>
          get(e.input.)
      }
      add(method, )

      DecodeInputs(e.input, new FinatraDecodeInputsContext)
      e.serverLogic(logic)
    }
  }

}
