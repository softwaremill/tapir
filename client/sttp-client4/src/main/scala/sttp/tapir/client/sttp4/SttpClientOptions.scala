package sttp.tapir.client.sttp4

import sttp.tapir.{Defaults, Endpoint, TapirFile}

case class SttpClientOptions(
    createFile: () => TapirFile,
    showEndpoint: Endpoint[_, _, _, _, _] => String
)

object SttpClientOptions {
  val default: SttpClientOptions = SttpClientOptions(Defaults.createTempFile, _.show)
  val defaultWithCompactShow: SttpClientOptions = SttpClientOptions(
    Defaults.createTempFile,
    e => s"[${e.info.name.getOrElse("<unnamed>")}] [${e.method.map(_.method).getOrElse("<no method>")}] ${e.showPathTemplate()}"
  )
}
