package tapir.client.sttp

import java.io.File

import tapir.Defaults

case class SttpClientOptions(createFile: () => File) // TODO: change to ResponseMetadata once available

object SttpClientOptions {
  implicit val Default: SttpClientOptions = SttpClientOptions(Defaults.createTempFile)
}
