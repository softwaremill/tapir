package sttp.tapir.docs.asyncapi

import sttp.tapir.apispec.ExtensionValue
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

private[asyncapi] object DocsExtensions {
  def fromIterable(docsExtensions: Iterable[DocsExtension[_]]): ListMap[String, ExtensionValue] =
    docsExtensions.map(e => (e.key, ExtensionValue(e.rawValue))).toListMap
}
