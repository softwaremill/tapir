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

  /** An untrusted string as a complete, escaped Scala string literal (surrounding quotes included). Prefer this over hand-writing
    * `"\"" + escapeString(x) + "\""` so the escape and the quotes can't get out of sync.
    */
  def quote(str: String): String = "\"" + escapeString(str) + "\""
}

object NameHelpers {
  val reservedKeys: Set[String] = VersionedHelpers.reservedKeys.toSet

  // A backtick-quoted Scala identifier can contain almost any character, but NOT a backtick or a line
  // terminator/control character. A name from an (untrusted) OpenAPI document that contains such a character
  // therefore cannot be safely emitted as an identifier and could otherwise break out of the quoting to inject
  // arbitrary code, so we reject it rather than emit it raw. See GHSA-gpcc-36pq-8qxr.
  private def backtickQuoteOrReject(kind: String, s: String): String =
    if (s.isEmpty || s.exists(c => c == '`' || c.isControl))
      throw new IllegalArgumentException(
        s"Cannot generate a safe Scala identifier from $kind '$s': it must be non-empty and must not contain backticks or control characters (see GHSA-gpcc-36pq-8qxr)"
      )
    else s"`$s`"

  def safeVariableName(s: String): String =
    if (!(reservedKeys ++ Set("enum", "given", "using")).contains(s) && s.matches("[A-Za-z_$][A-Za-z_$0-9]*")) s
    else backtickQuoteOrReject("name", s)

  // Enum member values become `case object`/`enum case` identifiers. Same backtick-escape gap as safeVariableName.
  def safeEnumMemberName(s: String): String =
    if (s.matches("[a-zA-Z][a-zA-Z0-9_]*")) s else backtickQuoteOrReject("enum value", s)

  // The generated `CodecFormat` class name for a content type. The definition site and every reference must produce
  // the same identifier, so this single derivation is the source of truth (and safely quotes/rejects the content type).
  def codecFormatName(contentType: String): String = safeVariableName(contentType + "CodecFormat")

  // Derives the class name of a schema nested under `parentName` at property `key`. Shared by the class generator and
  // the json serde generators so that emitted codec types cannot drift from the generated class names.
  // Derives a nested class/type identifier by camel-casing `key`. Every character the (untrusted) name may legally
  // contain but that is illegal in a raw Scala identifier — `_`, `-`, `.`, `+` (`$` is a valid identifier char) — is
  // treated as a word separator, so any name that passes NameValidation's [A-Za-z0-9._$+-] set yields a valid
  // identifier rather than non-compiling generated code. See GHSA-gpcc-36pq-8qxr.
  def addName(parentName: String, key: String): String =
    parentName + key.replace('_', ' ').replace('-', ' ').replace('.', ' ').replace('+', ' ').capitalize.replace(" ", "")

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
