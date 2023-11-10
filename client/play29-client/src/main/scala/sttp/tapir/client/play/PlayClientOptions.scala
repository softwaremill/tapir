package sttp.tapir.client.play

import sttp.tapir.{Defaults, TapirFile}

case class PlayClientOptions(createFile: () => TapirFile)

object PlayClientOptions {
  val default: PlayClientOptions = PlayClientOptions(Defaults.createTempFile)
}
