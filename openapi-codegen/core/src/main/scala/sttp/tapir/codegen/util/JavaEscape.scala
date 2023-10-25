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
