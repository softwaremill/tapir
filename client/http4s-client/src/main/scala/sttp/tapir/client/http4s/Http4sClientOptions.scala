package sttp.tapir.client.http4s

import sttp.tapir.Defaults

import java.io.File

case class Http4sClientOptions(createFile: () => File)

object Http4sClientOptions {
  val default: Http4sClientOptions = Http4sClientOptions(Defaults.createTempFile)
}
