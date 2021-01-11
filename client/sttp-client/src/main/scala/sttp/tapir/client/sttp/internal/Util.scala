package sttp.tapir.client.sttp.internal

import sttp.tapir.Endpoint

object Util {
  def throwError[I, E, O, R](endpoint: Endpoint[I, E, O, R], agr: I, e: E): Nothing = {
    val desc = endpoint.info.name.fold("")(name => s" $name")
    sys.error(s"Server$desc returned error $e. Argument: $agr")
  }
}
