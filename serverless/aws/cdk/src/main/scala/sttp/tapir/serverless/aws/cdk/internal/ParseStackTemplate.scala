package sttp.tapir.serverless.aws.cdk.internal

import cats.effect.Sync

object ParseStackTemplate {
  private val spacesNo: Int = 4

  def apply[F[_]: Sync](content: String, stackFile: StackFile, rs: Seq[Request]): F[String] =
    Sync[F].delay {
      stackFile.getFields
        .map { placeholder => (s: String) =>
          s.replace(s"{{$placeholder}}", stackFile.getValue(placeholder))
        }
        .foldLeft(content)((content, replace) => replace(content))
        .replace(
          "{{stacks}}",
          TreeToTypeScript(Tree.fromRequests(rs.toList))
            .map(i => if (i.trim.nonEmpty) " " * spacesNo + i else "")
            .mkString(System.lineSeparator())
        )
    }
}
