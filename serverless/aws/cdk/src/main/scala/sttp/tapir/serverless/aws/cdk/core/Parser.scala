package sttp.tapir.serverless.aws.cdk.core

import cats.effect.Sync
import sttp.tapir.server.ServerEndpoint
import cats.implicits.{toFunctorFilterOps, toFunctorOps}

//todo add comment to TS code
class Parser[F[_]: Sync](implicit reader: FileReader[F]) {
  def parse(path: String, values: StackFile, endpoints: Set[ServerEndpoint[Any, F]]): Either[Throwable, F[String]] = {
    val processors: List[String => String] = values.productElementNames.toList.zipWithIndex.map { case (placeholder, counter) =>
      s => s.replace(s"{{$placeholder}}", values.productElement(counter).toString)
    }

    val value = endpoints
      .map(e => Request.fromEndpoint(e.endpoint))
      .toList
      .flattenOption

    value match {
      case Nil => Left(new RuntimeException("No single valid endpoint to generate stack"))
      case requests =>
        Right {
          val generator = SuperGenerator
          val stacks = generator
            .generate(Resource.generate(Tree.build(requests)))
            .map(i => if (i != "\n") s"    $i" else "") // fixme dynamic number of spaces
            .mkString("\n") // fixme ugly

          reader.getContent(path).map(processors.foldLeft(_)((prev, f) => f(prev))).map(c => c.replace("{{stacks}}", stacks))
        }
    }
  }
}
