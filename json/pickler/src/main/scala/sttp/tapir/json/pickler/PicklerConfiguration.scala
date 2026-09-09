package sttp.tapir.json.pickler

import sttp.tapir.generic.Configuration

/** Configuration parameters for [[Pickler]] derivation.
  *
  * A single instance of this drives both halves of the derivation: the tapir `Schema` and the jsoniter-scala `JsonValueCodec`.
  *
  * @param genericDerivationConfig
  *   basic configuration for schema and codec derivation
  * @param transientNone
  *   skip serialization of Option fields if their value is None. If false, None will be serialized as null.
  */
final case class PicklerConfiguration(genericDerivationConfig: Configuration, transientNone: Boolean = true) {
  export genericDerivationConfig.{toEncodedName, toDiscriminatorValue}

  def discriminator: String = genericDerivationConfig.discriminator.getOrElse(PicklerConfiguration.DefaultTagKey)

  def withSnakeCaseMemberNames: PicklerConfiguration = PicklerConfiguration(genericDerivationConfig.withSnakeCaseMemberNames)
  def withScreamingSnakeCaseMemberNames: PicklerConfiguration = PicklerConfiguration(
    genericDerivationConfig.withScreamingSnakeCaseMemberNames
  )
  def withKebabCaseMemberNames: PicklerConfiguration = PicklerConfiguration(genericDerivationConfig.withKebabCaseMemberNames)
  def withDiscriminator(d: String): PicklerConfiguration = PicklerConfiguration(genericDerivationConfig.withDiscriminator(d))
  def withToEncodedName(toEncodedName: String => String) = PicklerConfiguration(genericDerivationConfig.copy(toEncodedName = toEncodedName))
  def withSnakeCaseDiscriminatorValues: PicklerConfiguration = PicklerConfiguration(
    genericDerivationConfig.withSnakeCaseDiscriminatorValues
  )
  def withScreamingSnakeCaseDiscriminatorValues: PicklerConfiguration = PicklerConfiguration(
    genericDerivationConfig.withScreamingSnakeCaseDiscriminatorValues
  )
  def withKebabCaseDiscriminatorValues: PicklerConfiguration = PicklerConfiguration(
    genericDerivationConfig.withKebabCaseDiscriminatorValues
  )
  def withFullDiscriminatorValues: PicklerConfiguration = PicklerConfiguration(genericDerivationConfig.withFullDiscriminatorValues)
  def withFullSnakeCaseDiscriminatorValues: PicklerConfiguration = PicklerConfiguration(
    genericDerivationConfig.withFullSnakeCaseDiscriminatorValues
  )
  def withFullKebabCaseDiscriminatorValues: PicklerConfiguration = PicklerConfiguration(
    genericDerivationConfig.withFullKebabCaseDiscriminatorValues
  )
  def withTransientNone(transientNone: Boolean): PicklerConfiguration = copy(transientNone = transientNone)
}

object PicklerConfiguration {

  /** The default discriminator field name. Previously this came from `upickle.core.Annotator.defaultTagKey`; it is inlined here so that the
    * module carries no uPickle dependency.
    */
  final val DefaultTagKey = "$type"

  given default: PicklerConfiguration = PicklerConfiguration(
    Configuration.default.copy(discriminator = Some(DefaultTagKey))
  )
}
