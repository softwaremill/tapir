package sttp.tapir.client.sttp

import sttp.client3.{FetchBackend, SttpBackend}
import sttp.model.Uri
import sttp.tapir.{Endpoint, PublicEndpoint}

import scala.concurrent.Future

trait SttpClientInterpreterExtensions { this: SttpClientInterpreter =>

  // public

  /** Interprets the public endpoint as a synchronous client call, using the given `baseUri` as the starting point to create the target uri.
    * If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using an asynchronous backend, and the result of decoding the response
    * (error or success value) is returned. If decoding the result fails, a failed future is returned.
    */
  def toQuickClient[I, E, O](e: PublicEndpoint[I, E, O, Any], baseUri: Option[Uri]): I => Future[Either[E, O]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val backend: SttpBackend[Future, Any] = FetchBackend()
    SttpClientInterpreter().toClientThrowDecodeFailures(e, baseUri, backend)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using an asynchronous backend, and the result (success value) is
    * returned. If decoding the result fails, or if the response corresponds to an error value, a failed future is returned.
    */
  def toQuickClientThrowErrors[I, E, O](e: PublicEndpoint[I, E, O, Any], baseUri: Option[Uri]): I => Future[O] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val backend: SttpBackend[Future, Any] = FetchBackend()
    SttpClientInterpreter().toClientThrowErrors(e, baseUri, backend)
  }

  // secure

  /** Interprets the secure endpoint as a synchronous client call, using the given `baseUri` as the starting point to create the target uri.
    * If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using an asynchronous backend, and the result of
    * decoding the response (error or success value) is returned. If decoding the result fails, a failed future is returned.
    */
  def toQuickSecureClient[A, I, E, O](e: Endpoint[A, I, E, O, Any], baseUri: Option[Uri]): A => I => Future[Either[E, O]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val backend: SttpBackend[Future, Any] = FetchBackend()
    SttpClientInterpreter().toSecureClientThrowDecodeFailures(e, baseUri, backend)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using an asynchronous backend, and the result
    * (success value) is returned. If decoding the result fails, or if the response corresponds to an error value, a failed future is
    * returned.
    */
  def toQuickSecureClientThrowErrors[A, I, E, O](e: Endpoint[A, I, E, O, Any], baseUri: Option[Uri]): A => I => Future[O] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val backend: SttpBackend[Future, Any] = FetchBackend()
    SttpClientInterpreter().toSecureClientThrowErrors(e, baseUri, backend)
  }
}
