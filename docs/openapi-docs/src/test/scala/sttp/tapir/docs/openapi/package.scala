package sttp.tapir.docs

import scala.io.Source

package object openapi {
  private[openapi] def load(fileName: String): String = {
    noIndentation(
      Source.fromInputStream(classOf[sttp.tapir.docs.openapi.VerifyYamlTest].getResourceAsStream(s"/$fileName")).getLines().mkString("\n")
    )
  }
  private[openapi] def noIndentation(s: String): String = s.replaceAll("[ \t]", "").trim
}
