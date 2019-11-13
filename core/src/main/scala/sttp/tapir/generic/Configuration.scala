package sttp.tapir.generic

import java.util.regex.Pattern

final case class Configuration(toLowLevelName: String => String) {
  def withSnakeCaseMemberNames: Configuration = copy(
    toLowLevelName = Configuration.snakeCaseTransformation
  )

  def withKebabCaseMemberNames: Configuration = copy(
    toLowLevelName = Configuration.kebabCaseTransformation
  )
}

object Configuration {
  implicit val default: Configuration = Configuration(Predef.identity)

  private val basePattern: Pattern = Pattern.compile("([A-Z]+)([A-Z][a-z])")
  private val swapPattern: Pattern = Pattern.compile("([a-z\\d])([A-Z])")

  private val snakeCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1_$2")
    swapPattern.matcher(partial).replaceAll("$1_$2").toLowerCase
  }

  private val kebabCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1-$2")
    swapPattern.matcher(partial).replaceAll("$1-$2").toLowerCase
  }
}
