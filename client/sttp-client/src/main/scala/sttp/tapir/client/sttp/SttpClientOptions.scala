package sttp.tapir.client.sttp

import sttp.tapir.{Defaults, TapirFile}

case class SttpClientOptions(createFile: () => TapirFile)

object SttpClientOptions {
  implicit val default: SttpClientOptions = SttpClientOptions(Defaults.createTempFile)
}
