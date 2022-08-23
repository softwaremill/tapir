package sttp.tapir.serverless.aws.cdk.core

import cats.effect.{Sync, kernel}
import sttp.tapir.server.ServerEndpoint

import scala.io.Source
import cats.implicits.{toFunctorOps, toTraverseOps}

//todo add comment to TS code
class Parser[F[_]: Sync] {
  def parse(path: String, values: StackFile, endpoints: Set[ServerEndpoint[Any, F]]): F[String] = {
    val content: F[String] = file(path).use(content => Sync[F].delay(content.getLines().mkString("\n"))) //fixme
    val processors: List[String => String] = values.productElementNames.toList.zipWithIndex.map { case (placeholder, counter) =>
      s => s.replace(s"{{$placeholder}}", values.productElement(counter).toString)
    }

    val requests: List[Request] = endpoints.map(e => Request.fromEndpoint(e.endpoint)).toList.sequence.get // fixme
    val tree = Tree.build(requests)
    val resources = Resource.generate(tree)

    val generator = SuperGenerator
    val stacks = generator.generate(resources).map(i => if (i != "\n") s"    $i" else "").mkString("\n") // fixme ugly

    content.map(processors.foldLeft(_)((prev, f) => f(prev))).map(c => c.replace("{{stacks}}", stacks))
  }

  // fixme parser should not be responsible for reading file
  private val file: String => kernel.Resource[F, Source] = o =>
    cats.effect.Resource.make[F, Source](
      Sync[F].blocking(Source.fromInputStream(getClass.getResourceAsStream(o)))
    )(w => Sync[F].blocking(w.close()))
}
