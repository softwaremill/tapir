package tapir.client.sttp

import java.io.File

import sttp.client.ResponseMetadata
import tapir.Defaults

case class SttpClientOptions(createFile: ResponseMetadata => File)

object SttpClientOptions {
  implicit val default: SttpClientOptions = SttpClientOptions(_ => Defaults.createTempFile())
}
