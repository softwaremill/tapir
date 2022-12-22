package sttp.tapir.docs.apispec

import sttp.apispec.ExtensionValue
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

private[docs] object DocsExtensions {
  def fromIterable(docsExtensions: Iterable[DocsExtension[_]]): ListMap[String, ExtensionValue] =
    docsExtensions.map(e => (e.key, ExtensionValue(e.rawValue))).toListMap
}
