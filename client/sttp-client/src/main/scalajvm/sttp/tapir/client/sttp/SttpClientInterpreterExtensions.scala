package sttp.tapir.client.sttp

import sttp.client3.HttpURLConnectionBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.Uri
import sttp.tapir.Endpoint

import scala.concurrent.Future

trait SttpClientInterpreterExtensions {

  /** Interprets the endpoint as a synchronous client call, using the given `baseUri` as the starting point to create
    * the target uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using a synchronous backend,
    * and the result of decoding the response (error or success value) is returned. If decoding the result fails,
    * an exception is thrown.
    */
  def toQuickClient[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Option[Uri])(implicit
      clientOptions: SttpClientOptions
  ): I => Either[E, O] = {
    val backend = HttpURLConnectionBackend()
    SttpClientInterpreter.toClientThrowDecodeFailures(e, baseUri, backend)
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using a synchronous backend,
    * and the result (success value) is returned. If decoding the result fails, or if the response corresponds to an
    * error value, an exception is thrown.
    */
  def toQuickClientThrowErrors[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Option[Uri])(implicit
      clientOptions: SttpClientOptions
  ): I => O = {
    val backend = HttpURLConnectionBackend()
    SttpClientInterpreter.toClientThrowErrors(e, baseUri, backend)
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using an async-http-client,
    * and the result `Future` is returned. If decoding the result fails, or if the response corresponds to an
    * error value, resulting future contains exception.
    * @note Using this method requires adding `com.softwaremill.sttp.client3:async-http-client-backend-future`
    *       dependency to your project
    */
  def toAsyncClientThrowErrors[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Option[Uri])(implicit
                                                                                         clientOptions: SttpClientOptions
  ): I => Future[O] =
    SttpClientInterpreter.toClientThrowErrors(e, baseUri, AsyncHttpClientFutureBackend())

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using an async-http-client,
    * and the result `Future` is returned. If decoding the result fails, resulting future contains exception.
    * If the response has been received and decoded, the future succeeds with the value or an error received.
    * @note Using this method requires adding `com.softwaremill.sttp.client3:async-http-client-backend-future`
    *       dependency to your project
    */
  def toAsyncClient[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Option[Uri])(implicit
                                                                                         clientOptions: SttpClientOptions
  ): I => Future[Either[E, O]] =
    SttpClientInterpreter.toClientThrowDecodeFailures(e, baseUri, AsyncHttpClientFutureBackend())
}
