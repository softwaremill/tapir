package codegen

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class BasicGeneratorSpec extends AnyFlatSpec with Matchers with Checkers {

  it should "generate the bookshop example" in {
    BasicGenerator.generateObjects(TestHelpers.myBookshopDoc)
  }

}
