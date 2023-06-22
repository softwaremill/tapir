package sttp.tapir.json.upickle.auto

import upickle.AttributeTagged
import upickle.implicits.MacrosCommon
import upickle.implicits.macros
import scala.reflect.ClassTag
import scala.deriving.Mirror
import upickle.core.ObjVisitor
import upickle.core.ArrVisitor
import upickle.core.Visitor

trait FieldNameCaseStrategy {

}
trait SnakeCaseSupport {
  this: MacrosCommon =>

  def camelToSnake(s: String) = {
    s.replaceAll("([A-Z])", "#$1").split('#').map(_.toLowerCase).mkString("_")
  }
  def snakeToCamel(s: String) = {
    val res = s.split("_", -1).map(x => s"${x(0).toUpper}${x.drop(1)}").mkString
    s"${s(0).toLower}${res.drop(1)}"
  }

  def snake_objectAttributeKeyReadMap(s: CharSequence): CharSequence =
    snakeToCamel(s.toString)
  def snake_objectAttributeKeyWriteMap(s: CharSequence): CharSequence =
    camelToSnake(s.toString)

  def snake_objectTypeKeyReadMap(s: CharSequence): CharSequence =
    snakeToCamel(s.toString)
  def snake_objectTypeKeyWriteMap(s: CharSequence): CharSequence =
    camelToSnake(s.toString)

  override def objectAttributeKeyReadMap(s: CharSequence): CharSequence =
    snakeToCamel(s.toString)

  override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence =
    camelToSnake(s.toString)

  override def objectTypeKeyReadMap(s: CharSequence): CharSequence =
    snakeToCamel(s.toString)

  override def objectTypeKeyWriteMap(s: CharSequence): CharSequence =
    camelToSnake(s.toString)
}

sealed trait FieldNameCase

case object Snake extends FieldNameCase
case object Camel extends FieldNameCase

sealed trait EnumValueEncoding

case object AsOrdinalString extends EnumValueEncoding
case object AsOrdinalInt extends EnumValueEncoding
case object AsCamelName extends EnumValueEncoding
case object AsSnakeName extends EnumValueEncoding
case object AsScreamingSnake extends EnumValueEncoding

sealed trait InheritanceReaderStrategy

object InheritanceReaderStrategy {
  case class DiscriminatorField(name: String) extends InheritanceReaderStrategy
  case object Default extends InheritanceReaderStrategy
}

// Global
case class CodecConfiguration(
    fieldNameCase: FieldNameCase,
    inheritanceReaderStrategy: InheritanceReaderStrategy,
    discriminatorField: Option[String] // default is $type
)

case class ClassCodecConfiguration(
  globalConfiguration: CodecConfiguration,
  fieldEncodedNames: Map[String, String],
  fieldDefaultValues: Map[String, Any]
  )

/**
 * Represents (de)serialization entrypoint for a specific case class T
  *
  * @param codecConfiguration
  */
class TapirPickleBase[T: ClassTag](codecConfiguration: ClassCodecConfiguration)(using Mirror.Of[T]) extends TapirPickle[T] {

  /** Custom name for the field containing Scala type */
  // override lazy val tagName = "$customType"

  // but field VALUE can apparently only be given as a constant with class annotation @key

  inline def deriveRW = macroRW[T]

  inline def withDefaultsRW: ReadWriter[T] =
    ReadWriter.join(macroR[T] match {
    case c: CaseClassReadereader[T] => new CaseClassReadereader[T](macros.paramsCount[T], macros.checkErrorMissingKeysCount[T]()) {
      override def visitors0 = c.visitors0
      override def fromProduct(p: Product): T = c.fromProduct(p)

      override def keyToIndex(x: String) = c.keyToIndex(x)
      override def allKeysArray: Array[String] = c.allKeysArray
   
      // This is how we can force our own custom default
      override def storeDefaults(x: upickle.implicits.BaseCaseObjectContext) = 
        x.storeValueIfNotFound(1, "custom default!")

    }
    case other => other

  }, macroW[T])
}

trait TapirPickle[T] extends AttributeTagged

class ConfiguredTapirPickle[T](delegate: TapirPickle[T], val ccConfig: ClassCodecConfiguration) extends TapirPickle[T] {

  override def objectAttributeKeyReadMap(s: CharSequence): CharSequence =
    delegate.objectAttributeKeyWriteMap(s)

  override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence =
    delegate.objectAttributeKeyWriteMap(s)

  override def objectTypeKeyReadMap(s: CharSequence): CharSequence =
    delegate.objectTypeKeyReadMap(s)

  override def objectTypeKeyWriteMap(s: CharSequence): CharSequence =
    delegate.objectTypeKeyWriteMap(s)
}

trait KeyReplacementSupport extends MacrosCommon {
 
  def ccConfig: ClassCodecConfiguration


  override def objectAttributeKeyReadMap(s: CharSequence): CharSequence =    
    super.objectAttributeKeyWriteMap(s) // TODO

  override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence =
    ccConfig.fieldEncodedNames.get(s.toString).getOrElse(super.objectAttributeKeyWriteMap(s))

  override def objectTypeKeyReadMap(s: CharSequence): CharSequence =
    super.objectTypeKeyReadMap(s) // TODO

  override def objectTypeKeyWriteMap(s: CharSequence): CharSequence =
    super.objectTypeKeyWriteMap(s) // TODO
}
object TapirPickle {
 
  // Possibly should be optimized to not create a new TapirPickle instance for every type T
  inline def deriveConfigured[T: ClassTag](using globalConfig: CodecConfiguration)(using Mirror.Of[T]): TapirPickle[T] =
    val config: ClassCodecConfiguration = sttp.tapir.json.upickle.auto.caseClassConfiguration[T](globalConfig)    
    val picklerWithCase = config.globalConfiguration.fieldNameCase match {
      case Snake => new TapirPickleBase(config) with SnakeCaseSupport
      case Camel => new TapirPickleBase(config)
    }

    val picklerWithCustomNames = if (config.fieldEncodedNames.nonEmpty)
      new ConfiguredTapirPickle(picklerWithCase, config) with KeyReplacementSupport
    else
      picklerWithCase

    picklerWithCustomNames

    // TODO support for discriminator field override
    //
    // val picklerWithDefaultValues = if hasDefaultValues(config)
    //   setCustomValues(picklerWithCustomNames)
    // else
    //   picklerWithCustomNames
    //
    // picklerWithDefaultValues

}
