package sttp.tapir.client.play

import java.io.File

import sttp.tapir.Defaults

case class PlayClientOptions(createFile: () => File)

object PlayClientOptions {
  implicit val default: PlayClientOptions = PlayClientOptions(Defaults.createTempFile)
}
