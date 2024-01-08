package sttp.tapir.perf.apis

import sttp.tapir._
import sttp.tapir.perf.Common._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.EndpointExtensions._

trait Endpoints {
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
      .maxRequestBodyLength(LargeInputSize + 1024L)
      .errorOut(stringBody)
      .out(stringBody)
  }

  val gen_post_in_bytes_out_string: EndpointGen = { (n: Int) =>
    endpoint.post
      .in("pathBytes" + n.toString)
      .in(path[Int]("id"))
      .in(byteArrayBody)
      .maxRequestBodyLength(LargeInputSize + 1024L)
      .errorOut(stringBody)
      .out(stringBody)
  }

  val gen_post_in_file_out_string: EndpointGen = { (n: Int) =>
    endpoint.post
      .in("pathFile" + n.toString)
      .in(path[Int]("id"))
      .in(fileBody)
      .maxRequestBodyLength(LargeInputSize + 1024L)
      .errorOut(stringBody)
      .out(stringBody)
  }

  val allEndpoints =
    List(gen_get_in_string_out_string, gen_post_in_string_out_string, gen_post_in_bytes_out_string, gen_post_in_file_out_string)

  def replyingWithDummyStr[F[_]](endpointGens: List[EndpointGen], reply: String => F[String]): Seq[ServerEndpointGen[F]] =
    endpointGens.map(gen => gen.andThen(se => se.serverLogicSuccess[F](_ => reply("ok"))))

  def genServerEndpoints[F[_]](gens: Seq[ServerEndpointGen[F]])(routeCount: Int): Seq[ServerEndpoint[Any, F]] =
    gens.flatMap(gen => (0 to routeCount).map(i => gen(i)))
}
