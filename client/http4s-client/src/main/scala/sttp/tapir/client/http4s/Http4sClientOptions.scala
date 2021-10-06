package sttp.tapir.client.http4s

import sttp.tapir.{Defaults, TapirFile}

case class Http4sClientOptions(createFile: () => TapirFile)

object Http4sClientOptions {
  val default: Http4sClientOptions = Http4sClientOptions(Defaults.createTempFile)
}
