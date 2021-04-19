package sttp.tapir.docs.openapi

import sttp.tapir.DocsExtension
import sttp.tapir.apispec.DocsExtensionValue
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

private[openapi] object DocsExtensions {
  def fromIterable(docsExtensions: Iterable[DocsExtension[_]]): ListMap[String, DocsExtensionValue] =
    docsExtensions.map(e => (e.key, DocsExtensionValue(e.rawValue))).toListMap
}
