package tapir

package object server {
  type StatusMapper[T] = T => StatusCode
  type DecodeFailureHandler[R] = (R, EndpointInput.Single[_], DecodeFailure) => DecodeFailureHandling
}
