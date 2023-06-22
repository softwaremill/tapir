package sttp.tapir.json.upickle.auto

import upickle.AttributeTagged
import upickle.implicits.MacrosCommon
import upickle.implicits.macros
import scala.reflect.ClassTag
import scala.deriving.Mirror
import upickle.core.ObjVisitor
import upickle.core.ArrVisitor
import upickle.core.Visitor

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

case class DiscriminatorField(name: String) extends InheritanceReaderStrategy

// Global
case class CodecConfiguration(
    fieldNameCase: FieldNameCase,
    enumValueEncoding: EnumValueEncoding,
    inheritanceReaderStrategy: InheritanceReaderStrategy
)

/** Task: write upickle usage that can take a configuration object which: a) maps CONCRETE case class field name to another name b) default
  * value of a field if it's missing c) enum value encoding - numbers, strings, camel case, etc
  */
class TapirPickle(codecConfiguration: CodecConfiguration) extends AttributeTagged {

  /** Custom name for the field containing Scala type */
  // override lazy val tagName = "$customType"

  // but field VALUE can apparently only be given as a constant with class annotation @key

  inline def deriveRW[T: ClassTag](using Mirror.Of[T]) = macroRW


  inline def withDefaultsRW[T: ClassTag](using Mirror.Of[T]): ReadWriter[T] =
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
