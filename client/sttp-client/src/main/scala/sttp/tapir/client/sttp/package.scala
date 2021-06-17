package sttp.tapir.client

package object sttp {
  def apply(clientOptions: SttpClientOptions = SttpClientOptions.default): TapirSttpClient = {
    new TapirSttpClient {
      override def sttpClientOptions: SttpClientOptions = clientOptions
    }
  }
}
