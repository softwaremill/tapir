package sttp.tapir.client.sttp4.stream

import sttp.capabilities.WebSockets

// Type class to ensure S extends Streams[S] but NOT WebSockets
trait StreamsNotWebSockets[S]
object StreamsNotWebSockets {
  // This implicit is available if S does NOT extend WebSockets
  implicit def allowIfNotWebSockets[S]: StreamsNotWebSockets[S] = new StreamsNotWebSockets[S] {}

  // This implicit is triggered if S extends WebSockets, causing ambiguity
  implicit def disallowIfWebSockets[S <: WebSockets]: StreamsNotWebSockets[S] = ???
}
