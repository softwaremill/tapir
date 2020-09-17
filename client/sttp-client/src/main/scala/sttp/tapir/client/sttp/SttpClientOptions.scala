package sttp.tapir.client.sttp

import java.io.File

import sttp.tapir.Defaults

case class SttpClientOptions(createFile: () => File)

object SttpClientOptions {
  implicit val default: SttpClientOptions = SttpClientOptions(Defaults.createTempFile)
}
