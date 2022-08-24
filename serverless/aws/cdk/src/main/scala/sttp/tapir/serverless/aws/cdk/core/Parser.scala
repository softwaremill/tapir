package sttp.tapir.serverless.aws.cdk.core

import cats.effect.Sync
import sttp.tapir.server.ServerEndpoint
import cats.implicits.{toFunctorFilterOps, toFunctorOps}

//todo add comment to TS code
//fixme parser should get content to parse not the file path
//fixme parser should parse all files from template
class Parser[F[_]: Sync](spacesNo: Int = 4)(implicit reader: FileReader[F]) {
  def parse(path: String, values: StackFile, endpoints: Set[ServerEndpoint[Any, F]]): Either[Throwable, F[String]] = {
    val processors: List[String => String] =
      values.getFields().map { placeholder => (s: String) =>
        s.replace(s"{{$placeholder}}", values.getValue(placeholder))
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
            .map(i => if (!i.trim.isEmpty) " " * spacesNo + i else "")
            .mkString(System.lineSeparator())

          reader
            .getContent(path)
            .map(processors.foldLeft(_)((prev, f) => f(prev)))
            .map(c => c.replace("{{stacks}}", stacks))
        }
    }
  }
}
