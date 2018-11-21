package tapir.client.sttp

import cats.effect.IO
import tapir.Endpoint
import tapir.client.tests.ClientTests
import tapir.typelevel.ParamsAsArgs
import com.softwaremill.sttp._

class SttpClientTests extends ClientTests {
  private implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, args: I)(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): IO[Either[E, O]] = {

    IO(paramsAsArgs.applyFn(e.toSttpRequest(uri"http://localhost:$port"), args).send().unsafeBody)
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
