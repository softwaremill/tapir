package sttp.tapir.serverless.aws

import cats.effect.IO
import cats.implicits._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

package object cdk {

  //fixme is this good idea to keep those endpoints here?
  val hello: ServerEndpoint[Any, IO] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody).serverLogic { case (name: String) =>
      IO.pure(s"Hi! $name".asRight[Unit])
    }

  val hello_with_id: ServerEndpoint[Any, IO] =
    endpoint.get.in("hello" / path[Int]("id")).out(stringBody).serverLogic { case (id: Int) =>
      IO.pure(s"Hello from the other side id: $id".asRight[Unit])
    }

  val allEndpoints: Set[ServerEndpoint[Any, IO]] = Set(
    hello,
    hello_with_id
  )
}
