package sttp.tapir.codegen.util

object JavaEscape {
  def escapeString(str: String): String = {
    str.flatMap {
      case '\\' => "\\\\"
      case '"'  => "\\\""
      case '\n' => "\\n"
      case '\t' => "\\t"
      case '\r' => "\\r"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case char => char.toString
    }
  }
}

object NameHelpers {
  val reservedKeys: Set[String] =
    scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)

  def safeVariableName(s: String): String =
    if ((reservedKeys ++ Set("enum", "given", "using")).contains(s) || !s.matches("[A-Za-z_$][A-Za-z_$0-9]*")) s"`$s`" else s
}
