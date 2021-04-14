package sttp.tapir.docs.openapi

import sttp.tapir.Extension
import sttp.tapir.apispec.ExtensionValue
import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

object Extensions {
  def fromIterable(extensions: Iterable[Extension[_]]): ListMap[String, ExtensionValue] =
    extensions.map(e => (e.key, ExtensionValue(e.rawValue))).toListMap
}
