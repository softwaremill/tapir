package sttp.tapir.serverless.aws.cdk.test

import cats.effect.kernel.Sync
import cats.implicits._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

object TestEndpoints {
  def hello[F[_]: Sync]: ServerEndpoint[Any, F] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody).serverLogic { case (name: String) =>
      Sync[F].pure(s"Hi! $name".asRight[Unit])
    }

  def hello_with_id[F[_]: Sync]: ServerEndpoint[Any, F] =
    endpoint.get.in("hello" / path[Int]("id")).out(stringBody).serverLogic { case (id: Int) =>
      Sync[F].pure(s"Hello from the other side id: $id".asRight[Unit])
    }

  def all[F[_]: Sync]: Set[ServerEndpoint[Any, F]] = Set(
    hello[F],
    hello_with_id[F]
  )
}
