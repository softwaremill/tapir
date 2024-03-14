package sttp.tapir.generic

import sttp.tapir.Schema.SName

import java.util.regex.Pattern

/** @param toEncodedName
  *   A function which, given a field name of a case class, returns the encoded field name, as in the encoded representation of the case
  *   class.
  * @param discriminator
  *   Encoded field name, whose value should be used to choose the appropriate subtype of a [[sttp.tapir.SchemaType.SCoproduct]].
  * @param toDiscriminatorValue
  *   A function which, given the name of a subtype, returns the value of the discriminator field corresponding to that subtype. Used when
  *   creating [[sttp.tapir.SchemaType.SCoproduct]] schemas.
  */
final case class Configuration(toEncodedName: String => String, discriminator: Option[String], toDiscriminatorValue: SName => String) {
  def withSnakeCaseMemberNames: Configuration = copy(toEncodedName = Configuration.snakeCaseTransformation)
  def withScreamingSnakeCaseMemberNames: Configuration = copy(toEncodedName = Configuration.screamingSnakeCaseTransformation)
  def withKebabCaseMemberNames: Configuration = copy(toEncodedName = Configuration.kebabCaseTransformation)
  def withDiscriminator(d: String): Configuration = copy(discriminator = Some(d))
  def withSnakeCaseDiscriminatorValues: Configuration = copy(toDiscriminatorValue = Configuration.shortSnakeCaseSubtypeTransformation)
  def withScreamingSnakeCaseDiscriminatorValues: Configuration = copy(toDiscriminatorValue = Configuration.shortScreamingSnakeCaseSubtypeTransformation)
  def withKebabCaseDiscriminatorValues: Configuration = copy(toDiscriminatorValue = Configuration.shortKebabCaseSubtypeTransformation)
  def withFullDiscriminatorValues: Configuration = copy(toDiscriminatorValue = Configuration.fullIdentitySubtypeTransformation)
  def withFullSnakeCaseDiscriminatorValues: Configuration = copy(toDiscriminatorValue = Configuration.fullSnakeCaseSubtypeTransformation)
  def withFullKebabCaseDiscriminatorValues: Configuration = copy(toDiscriminatorValue = Configuration.fullKebabCaseSubtypeTransformation)
}

object Configuration {

  private val basePattern: Pattern = Pattern.compile("([A-Z]+)([A-Z][a-z])")
  private val swapPattern: Pattern = Pattern.compile("([a-z\\d])([A-Z])")

  private val snakeCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1_$2")
    swapPattern.matcher(partial).replaceAll("$1_$2").toLowerCase
  }

  private val screamingSnakeCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1_$2")
    swapPattern.matcher(partial).replaceAll("$1_$2").toUpperCase
  }

  private val kebabCaseTransformation: String => String = s => {
    val partial = basePattern.matcher(s).replaceAll("$1-$2")
    swapPattern.matcher(partial).replaceAll("$1-$2").toLowerCase
  }

  private val fullIdentitySubtypeTransformation: SName => String =
    _.fullName.stripSuffix("$")

  private val fullSnakeCaseSubtypeTransformation: SName => String =
    fullIdentitySubtypeTransformation.andThen(snakeCaseTransformation)

  private val fullKebabCaseSubtypeTransformation: SName => String =
    fullIdentitySubtypeTransformation.andThen(kebabCaseTransformation)

  private val shortIdentitySubtypeTransformation: SName => String =
    _.fullName.split('.').last.stripSuffix("$")

  private val shortSnakeCaseSubtypeTransformation: SName => String =
    shortIdentitySubtypeTransformation.andThen(snakeCaseTransformation)

  private val shortScreamingSnakeCaseSubtypeTransformation: SName => String =
    shortIdentitySubtypeTransformation.andThen(screamingSnakeCaseTransformation)

  private val shortKebabCaseSubtypeTransformation: SName => String =
    shortIdentitySubtypeTransformation.andThen(kebabCaseTransformation)

  implicit val default: Configuration = Configuration(Predef.identity, None, shortIdentitySubtypeTransformation)

}
