package tapir

package object server {
  type DecodeFailureHandler[R] = (R, EndpointInput.Single[_], DecodeFailure) => DecodeFailureHandling
}
