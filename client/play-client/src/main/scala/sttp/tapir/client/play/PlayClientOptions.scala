package sttp.tapir.client.play

import sttp.tapir.Defaults
import sttp.tapir.internal.TapirFile

case class PlayClientOptions(createFile: () => TapirFile)

object PlayClientOptions {
  val default: PlayClientOptions = PlayClientOptions(Defaults.createTempFile)
}
