package sttp.tapir

package object server {

  /**
    * Given the context in which a decode failure occurred (the request, the input and the failure), returns the action
    * that should be taken.
    *
    * See also [[DecodeFailureHandling]] and [[DefaultDecodeFailureHandler]].
    */
  type DecodeFailureHandler = DecodeFailureContext => DecodeFailureHandling
}
