package sttp.tapir.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AttributeMapTest extends AnyFlatSpec with Matchers {
  case class User(name: String)

  it should "get and set attributes" in {
    // given
    val m = new AttributeMap()

    // when
    val m2 = m.put(AttributeKey[User], User("x")).put(AttributeKey[String], "a").put(AttributeKey[String], "b")

    // then
    m2.get(AttributeKey[User]) shouldBe Some(User("x"))
    m2.get(AttributeKey[String]) shouldBe Some("b")
    m2.get(AttributeKey[Int]) shouldBe None
  }
}
