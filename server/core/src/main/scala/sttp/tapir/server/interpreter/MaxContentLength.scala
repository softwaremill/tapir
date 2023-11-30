package sttp.tapir.server.interpreter

/** Can be used as an endpoint attribute.
  * @example
  * {{{
  * endpoint.attribute(AttributeKey[MaxContentLength], MaxContentLength(16384L))
  * }}}
  */
case class MaxContentLength(value: Long)
