package sttp.tapir.client.sttp

import sttp.tapir.{Defaults, File}

case class SttpClientOptions(createFile: () => File)

object SttpClientOptions {
  val default: SttpClientOptions = SttpClientOptions(Defaults.createTempFile)
}
