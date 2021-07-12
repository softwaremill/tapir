package sttp.tapir.client.sttp

import sttp.client3.HttpURLConnectionBackend
import sttp.model.Uri
import sttp.tapir.Endpoint

trait SttpClientInterpreterExtensions {

  this: SttpClientInterpreter =>

  /** Interprets the endpoint as a synchronous client call, using the given `baseUri` as the starting point to create
    * the target uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using a synchronous backend,
    * and the result of decoding the response (error or success value) is returned. If decoding the result fails,
    * an exception is thrown.
    */
  def toQuickClient[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Option[Uri]): I => Either[E, O] = {
    val backend = HttpURLConnectionBackend()
    SttpClientInterpreter().toClientThrowDecodeFailures(e, baseUri, backend)
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using a synchronous backend,
    * and the result (success value) is returned. If decoding the result fails, or if the response corresponds to an
    * error value, an exception is thrown.
    */
  def toQuickClientThrowErrors[I, E, O](e: Endpoint[I, E, O, Any], baseUri: Option[Uri]): I => O = {
    val backend = HttpURLConnectionBackend()
    SttpClientInterpreter().toClientThrowErrors(e, baseUri, backend)
  }
}
