package sttp.tapir.json.pickler

import sttp.tapir.generic.Configuration

final case class PicklerConfiguration(genericDerivationConfig: Configuration) {
  export genericDerivationConfig.{toEncodedName, discriminator, toDiscriminatorValue}

  def withSnakeCaseMemberNames: PicklerConfiguration = PicklerConfiguration(genericDerivationConfig.withSnakeCaseMemberNames)
  def withScreamingSnakeCaseMemberNames: PicklerConfiguration = PicklerConfiguration(genericDerivationConfig.withScreamingSnakeCaseMemberNames)
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
}

object PicklerConfiguration {
  given default: PicklerConfiguration = PicklerConfiguration(
    Configuration.default.copy(discriminator = Some(SubtypeDiscriminator.DefaultFieldName))
  )
}
