 package sttp.tapir.generic

import java.util.regex.Pattern
import magnolia.TypeName

final case class Configuration(toEncodedName: String => String, discriminator: Option[String], toEncodedSubtypeName: TypeName => String) {
  def withSnakeCaseMemberNames: Configuration = copy(toEncodedName = Configuration.snakeCaseTransformation)
  def withKebabCaseMemberNames: Configuration = copy(toEncodedName = Configuration.kebabCaseTransformation)
  def withDiscriminator(d: String): Configuration = copy(discriminator = Some(d))
  def withSnakeCaseSubtypeNames: Configuration = copy(toEncodedSubtypeName = Configuration.snakeCaseSubtypeTransformation)
  def withKebabCaseSubtypeNames: Configuration = copy(toEncodedSubtypeName = Configuration.kebabCaseSubtypeTransformation)
}

object Configuration {

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

  private val identitySubtypeTransformation: TypeName => String =
    _.short.stripSuffix("$")

  private val snakeCaseSubtypeTransformation: TypeName => String =
    ((t: TypeName) => t.short).andThen(snakeCaseTransformation).andThen(_.stripSuffix("$"))

  private val kebabCaseSubtypeTransformation: TypeName => String =
    ((t: TypeName) => t.short).andThen(snakeCaseTransformation).andThen(_.stripSuffix("$"))

  implicit val default: Configuration = Configuration(Predef.identity, None, identitySubtypeTransformation)

}
