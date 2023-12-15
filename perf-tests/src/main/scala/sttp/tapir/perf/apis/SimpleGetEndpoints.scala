package sttp.tapir.perf.apis

import sttp.tapir._
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.EndpointExtensions._

trait SimpleGetEndpoints {
  type EndpointGen = Int => PublicEndpoint[_, String, String, Any]
  type ServerEndpointGen[F[_]] = Int => ServerEndpoint[Any, F]

  val gen_get_in_string_out_string: EndpointGen = { (n: Int) =>
    endpoint.get
      .in("path" + n.toString)
      .in(path[Int]("id"))
      .errorOut(stringBody)
      .out(stringBody)
  }

  val gen_post_in_string_out_string: EndpointGen = { (n: Int) =>
    endpoint.post
      .in("path" + n.toString)
      .in(path[Int]("id"))
      .in(stringBody)
      .errorOut(stringBody)
      .out(stringBody)
  }

  val gen_post_in_file_out_string: EndpointGen = { (n: Int) =>
    endpoint.post
      .in("pathFile" + n.toString)
      .in(path[Int]("id"))
      .in(fileBody)
      .maxRequestBodyLength(300000)
      .errorOut(stringBody)
      .out(stringBody)
  }

  def replyingWithDummyStr[F[_]](endpointGens: List[EndpointGen])(implicit
      monad: MonadError[F]
  ): Seq[ServerEndpointGen[F]] =
    endpointGens.map(gen => gen.andThen(se => se.serverLogicSuccess[F](_ => monad.eval("ok"))))

  def genServerEndpoints[F[_]](gens: Seq[ServerEndpointGen[F]])(routeCount: Int): Seq[ServerEndpoint[Any, F]] =
    gens.flatMap(gen => (0 to routeCount).map(i => gen(i)))
}
