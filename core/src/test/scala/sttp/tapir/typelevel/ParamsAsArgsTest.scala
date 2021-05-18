package sttp.tapir.typelevel

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ParamsAsArgsTest extends AnyFlatSpec with Matchers {
  it should "compile" in {
    // without aux
    implicitly[ParamsAsArgs[String]]
    implicitly[ParamsAsArgs[(Int, String)]]
  }

  it should "find the same class for a single param with and without aux" in {
    implicitly[ParamsAsArgs[String]].getClass shouldBe implicitly[ParamsAsArgs.Aux[String, String => *]].getClass
  }

  it should "find the same class for two params with and without aux" in {
    implicitly[ParamsAsArgs[(String, Int)]].getClass shouldBe implicitly[ParamsAsArgs.Aux[(String, Int), (String, Int) => *]].getClass
  }

  it should "convert single param to args" in {
    val singleAsFn = implicitly[ParamsAsArgs[String]].toFn((x: String) => x.length)
    singleAsFn.asInstanceOf[String => Int].apply("xxx") shouldBe 3
  }

  it should "convert two params to args" in {
    val singleAsFn = implicitly[ParamsAsArgs[(String, Int)]].toFn { case (x: String, y: Int) => x.length + y }
    singleAsFn.asInstanceOf[(String, Int) => Int].apply("xxx", 2) shouldBe 5
  }
}
