package sttp.tapir.docs

package object asyncapi {
  private[docs] type MessageKey = String

  def apply(docsOptions: AsyncAPIDocsOptions = AsyncAPIDocsOptions.default): TapirAsyncAPIDocs = {
    new TapirAsyncAPIDocs {
      override def asyncAPIDocsOptions: AsyncAPIDocsOptions = docsOptions
    }
  }
}
