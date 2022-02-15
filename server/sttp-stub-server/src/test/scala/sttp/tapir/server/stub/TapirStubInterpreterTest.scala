package sttp.tapir.server.stub

import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.Identity
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir._
import sttp.client3._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

class TapirStubInterpreterTest extends AnyFlatSpec with Matchers {

  import ProductsApi._

  val sttpBackendStub: SttpBackendStub[Identity, Nothing] = SttpBackendStub.apply(IdMonad)

  case class ServerOptions(interceptors: List[Interceptor[Identity]])
  object ServerOptions {
    def customInterceptors: CustomInterceptors[Identity, ServerOptions] =
      CustomInterceptors(createOptions = (ci: CustomInterceptors[Identity, ServerOptions]) => ServerOptions(ci.interceptors))
  }

  val options: CustomInterceptors[Identity, ServerOptions] = ServerOptions.customInterceptors

  val server = TapirStubInterpreter[Identity, Nothing, ServerOptions](sttpBackendStub, options)
    .stubEndpoint(getProduct)
    .whenInputMatches(_ => true)
    .thenSuccess(Product("T-shirt"))
    .stubServerEndpoint(createProduct)
    .whenRequestMatches()
    .thenLogic() // simplify + List[ServerEndpoint]
    .stubServerEndpointFull(updateProduct)
    .whenInputMatches(_ => true)
    .thenError(Error("Failed to update"), StatusCode.PreconditionFailed)
    .backend()

  it should "test" in {
    val response = SttpClientInterpreter().toRequestThrowDecodeFailures(updateProduct.endpoint, Some(uri"http://test.com"))
      .apply(("computer", Product("computer-2")))
      .send(server)

    println(response)
  }
}

object ProductsApi {

  case class Product(name: String)

  case class Error(msg: String)

  val getProduct: Endpoint[Unit, String, Error, Product, Any] = endpoint.get
    .in("api" / "products" / path[String]("name"))
    .out(jsonBody[Product])
    .errorOut(jsonBody[Error])

  val createProduct: ServerEndpoint[Nothing, Identity] = endpoint.post
    .in("api" / "products")
    .in(jsonBody[Product])
    .out(jsonBody[Product])
    .errorOut(jsonBody[Error])
    .serverLogic(product => IdMonad.unit(Right(product)))

  val updateProduct: Full[Unit, Unit, (String, Product), Error, Product, Any, Identity] = endpoint.put
    .in("api" / "products" / path[String]("name"))
    .in(jsonBody[Product])
    .out(jsonBody[Product])
    .errorOut(jsonBody[Error])
    .serverLogic { case (_, product) => IdMonad.unit(Right(product)) }

}
