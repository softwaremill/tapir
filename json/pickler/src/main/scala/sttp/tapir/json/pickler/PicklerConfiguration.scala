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

  def withSnakeCaseMemberNames: PicklerConfiguration = copy(genericDerivationConfig = genericDerivationConfig.withSnakeCaseMemberNames)
  def withScreamingSnakeCaseMemberNames: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withScreamingSnakeCaseMemberNames)
  def withKebabCaseMemberNames: PicklerConfiguration = copy(genericDerivationConfig = genericDerivationConfig.withKebabCaseMemberNames)
  def withDiscriminator(d: String): PicklerConfiguration = copy(genericDerivationConfig = genericDerivationConfig.withDiscriminator(d))
  def withToEncodedName(toEncodedName: String => String) =
    copy(genericDerivationConfig = genericDerivationConfig.copy(toEncodedName = toEncodedName))
  def withSnakeCaseDiscriminatorValues: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withSnakeCaseDiscriminatorValues)
  def withScreamingSnakeCaseDiscriminatorValues: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withScreamingSnakeCaseDiscriminatorValues)
  def withKebabCaseDiscriminatorValues: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withKebabCaseDiscriminatorValues)
  def withFullDiscriminatorValues: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withFullDiscriminatorValues)
  def withFullSnakeCaseDiscriminatorValues: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withFullSnakeCaseDiscriminatorValues)
  def withFullKebabCaseDiscriminatorValues: PicklerConfiguration =
    copy(genericDerivationConfig = genericDerivationConfig.withFullKebabCaseDiscriminatorValues)
  def withTransientNone(transientNone: Boolean): PicklerConfiguration = copy(transientNone = transientNone)
}

object PicklerConfiguration {

  /** The default discriminator field name. */
  final val DefaultTagKey = "$type"

  given default: PicklerConfiguration = PicklerConfiguration(
    Configuration.default.copy(discriminator = Some(DefaultTagKey))
  )
}
