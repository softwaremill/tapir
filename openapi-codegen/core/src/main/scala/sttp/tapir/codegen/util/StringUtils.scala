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
  val reservedKeys: Set[String] = VersionedHelpers.reservedKeys.toSet

  def safeVariableName(s: String): String =
    if ((reservedKeys ++ Set("enum", "given", "using")).contains(s) || !s.matches("[A-Za-z_$][A-Za-z_$0-9]*")) s"`$s`" else s

  // Derives the class name of a schema nested under `parentName` at property `key`. Shared by the class generator and
  // the json serde generators so that emitted codec types cannot drift from the generated class names.
  def addName(parentName: String, key: String): String =
    parentName + key.replace('_', ' ').replace('-', ' ').capitalize.replace(" ", "")

  def indent(i: Int)(str: String): String = {
    str.linesIterator.map(" " * i + _).mkString("\n")
  }

  def uncapitalise(name: String): String = name.head.toLower +: name.tail

  def strippedToCamelCase(string: String): String = string
    .split("[^0-9a-zA-Z$_]")
    .filter(_.nonEmpty)
    .zipWithIndex
    .map { case (part, 0) => part; case (part, _) => part.capitalize }
    .mkString
}
