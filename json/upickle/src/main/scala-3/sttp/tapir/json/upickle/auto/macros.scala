package sttp.tapir.json.upickle.auto

import scala.deriving.Mirror
import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.Type
import scala.reflect.ClassTag

/** Builds serialization configuration for a specific case class T. This macro merges the given global configuration with information read
  * from class annotations like @encodedName and others.
  *
  * @param config
  */
inline def caseClassConfiguration[T: ClassTag](config: CodecConfiguration)(using Mirror.Of[T]): ClassCodecConfiguration = ${
  caseClassConfigurationImpl('config)
}

def caseClassConfigurationImpl[T: Type](config: Expr[CodecConfiguration])(using Quotes): Expr[ClassCodecConfiguration] = '{new ClassCodecConfiguration($config, Map.empty, Map.empty)} // TODO Map.empty
