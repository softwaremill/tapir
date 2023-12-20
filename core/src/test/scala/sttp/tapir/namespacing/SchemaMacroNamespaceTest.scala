package sttp.tapir.namespacing

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SchemaMacroNamespaceTest extends AnyFlatSpec with Matchers {

  // #2254: the macro should reference fully qualified class names
  it should "compile macro-generated code using derivedEnumeration in a package other than sttp.tapir" in {
    sealed trait MyProduct
    object MyProduct {
      def fromString(m: String): Option[MyProduct] = ???
    }
    case object V1 extends MyProduct

    import sttp.tapir.Codec
    Codec.derivedEnumeration[String, MyProduct](MyProduct.fromString, _.toString)
  }

  it should "compile macro-generated code for shadowed _root_.scala names" in {

    assert(sttp.tapir.testdata.BuiltinTypenameCollisionEnum.schema.name.nonEmpty)
    assert(sttp.tapir.testdata.BuiltinTypenameCollisionCaseClass.schema.name.nonEmpty)
  }

}
