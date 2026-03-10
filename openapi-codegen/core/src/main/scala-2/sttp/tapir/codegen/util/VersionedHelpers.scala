package sttp.tapir.codegen.util

object VersionedHelpers {
  val reservedKeys: Seq[String] = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)
}
