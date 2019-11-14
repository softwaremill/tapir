package sttp.tapir

package object server {
  /**
    * Given the request, the input for which value decoding failed, and the decode value, returns the action
    * that should be taken.
    */
  type DecodeFailureHandler[-REQUEST] = (REQUEST, EndpointInput.Single[_], DecodeFailure) => DecodeFailureHandling
}
