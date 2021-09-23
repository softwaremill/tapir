package sttp.tapir.client.play

import sttp.tapir.{Defaults, File}

case class PlayClientOptions(createFile: () => File)

object PlayClientOptions {
  val default: PlayClientOptions = PlayClientOptions(Defaults.createTempFile)
}
