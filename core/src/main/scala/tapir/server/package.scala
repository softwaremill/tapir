package tapir

package object server {
  type DecodeFailureHandler[REQUEST] = (REQUEST, EndpointInput.Single[_], DecodeFailure) => DecodeFailureHandling
}
