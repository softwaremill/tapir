package sttp.tapir.serverless.aws.cdk.core

import cats.effect.{IO, kernel}
import sttp.tapir.server.ServerEndpoint

import scala.io.Source
import cats.implicits.toTraverseOps

//todo add comment to TS code
class Parser {

  // fixme use F[_]
  def parse(path: String, values: StackFile, endpoints: Set[ServerEndpoint[Any, IO]]): IO[String] = {
    val content: IO[String] = file(path).use(content => IO.delay(content.getLines().mkString("\n"))) // fixme separator
    val processors: List[String => String] = values.productElementNames.toList.zipWithIndex.map { case (placeholder, counter) =>
      s => s.replace(s"{{$placeholder}}", values.productElement(counter).toString)
    }

    val requests: List[Request] = endpoints.map(e => Request.fromEndpoint(e.endpoint)).toList.sequence.get // fixme
    val tree = Tree.build(requests)
    val resources = Resource.generate(tree)

    val generator = SuperGenerator
    val stacks = generator.generate(resources)

    content.map(processors.foldLeft(_)((prev, f) => f(prev))).map(c => c.replace("{{stacks}}", stacks))
  }

  // fixme parser should not be responsible for reading file
  val file: String => kernel.Resource[IO, Source] = o =>
    cats.effect.Resource.make[IO, Source](
      IO.blocking(Source.fromInputStream(getClass.getResourceAsStream(o)))
    )(w => IO.blocking(w.close()).void)
}
