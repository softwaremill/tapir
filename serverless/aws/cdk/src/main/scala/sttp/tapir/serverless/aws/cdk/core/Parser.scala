package sttp.tapir.serverless.aws.cdk.core

import cats.effect.Sync
import cats.syntax.all._
import sttp.tapir.server.ServerEndpoint

//todo add comment to TS code
//fixme parser should get content to parse not the file path
//fixme parser should parse all files from template
class Parser[F[_]: Sync](spacesNo: Int = 4)(implicit reader: FileReader[F]) {
  def parse(path: String, values: StackFile, endpoints: Set[ServerEndpoint[Any, F]]): Either[Throwable, F[String]] = {
    val processors: List[String => String] =
      values.getFields().map { placeholder => (s: String) =>
        s.replace(s"{{$placeholder}}", values.getValue(placeholder))
      }

    val requests = endpoints.flatMap(e => Request.fromEndpoint(e.endpoint)).toList

    requests match {
      case Nil => Left(new RuntimeException("No single valid endpoint to generate stack"))
      case rs =>
        Right {
          val generator = SuperGenerator
          val stacks = generator
            .generate(Resource.generate(Tree.build(rs)))
            .map(i => if (i.trim.nonEmpty) " " * spacesNo + i else "")
            .mkString(System.lineSeparator())

          reader
            .getContent(path)
            .map(processors.foldLeft(_)((prev, f) => f(prev)))
            .map(_.replace("{{stacks}}", stacks))
        }
    }
  }
}
