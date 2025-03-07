package sttp.tapir.client.sttp4

import sttp.tapir.{Defaults, TapirFile}

case class SttpClientOptions(createFile: () => TapirFile)

object SttpClientOptions {
  val default: SttpClientOptions = SttpClientOptions(Defaults.createTempFile)
}
