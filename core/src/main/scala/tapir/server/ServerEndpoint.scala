package tapir.server
import tapir.Endpoint

case class ServerEndpoint[I, E, O, +S, F[_]](endpoint: Endpoint[I, E, O, S], logic: I => F[Either[E, O]])
