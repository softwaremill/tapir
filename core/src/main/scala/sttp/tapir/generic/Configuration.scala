package sttp.tapir.generic

import java.util.regex.Pattern

final case class Configuration(toEncodedName: String => String, discriminator: Option[String]) {
  def withSnakeCaseMemberNames: Configuration = copy(toEncodedName = Configuration.snakeCaseTransformation)
  def withKebabCaseMemberNames: Configuration = copy(toEncodedName = Configuration.kebabCaseTransformation)
  def withDiscriminator(d: String): Configuration = copy(discriminator = Some(d))
}

object Configuration {
  implicit val default: Configuration = Configuration(Predef.identity, None)

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
