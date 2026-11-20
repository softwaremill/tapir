package sttp.tapir.json.pickler

import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID

/** ScalaCheck generators for the fixtures, shared by `SchemaCodecAgreementTest` and `DifferentialOracleTest`.
  *
  * Strings mix plain ASCII with characters that need escaping and non-BMP code points, so that both suites exercise the writer's escaping
  * paths; numbers cover the full range of each type.
  */
/** Fixtures that exist only for the property-based suites. */
object PropertyFixtures {
  case class ErrorCodeHolder(fieldA: Fixtures.ErrorCode, fieldB: String)
}

object Generators {
  import Fixtures.*
  import CodecFixtures.{Edge, MutualA, MutualB, Node, SimpleNode, Tree}
  import PropertyFixtures.*

  val genString: Gen[String] = Gen.frequency(
    5 -> Gen.alphaNumStr,
    2 -> Gen.asciiPrintableStr,
    1 -> Gen.oneOf("zażółć", "日本語", "😀 emoji", "tab\tnewline\n", "quote\"backslash\\", "\u0001control", ""),
    1 -> Arbitrary.arbString.arbitrary // not `arbitrary[String]`: that would be the (lazy) given below, i.e. this very generator
  )

  given Arbitrary[String] = Arbitrary(genString)

  given Arbitrary[UUID] = Arbitrary(Gen.uuid)

  given Arbitrary[FlatClass] = Arbitrary(for (a <- Arbitrary.arbitrary[Int]; b <- genString) yield FlatClass(a, b))
  given Arbitrary[InnerClass] = Arbitrary(Arbitrary.arbitrary[Int].map(InnerClass(_)))
  given Arbitrary[TopClass] = Arbitrary(for (a <- genString; b <- Arbitrary.arbitrary[InnerClass]) yield TopClass(a, b))
  given Arbitrary[AnnotatedInnerClass] = Arbitrary(for (a <- genString; b <- genString) yield AnnotatedInnerClass(a, b))
  given Arbitrary[TopClass2] = Arbitrary(for (a <- genString; b <- Arbitrary.arbitrary[AnnotatedInnerClass]) yield TopClass2(a, b))
  given Arbitrary[FlatClassWithOption] = Arbitrary(
    for (a <- genString; b <- Gen.option(Arbitrary.arbitrary[Int]); c <- Arbitrary.arbitrary[Boolean]) yield FlatClassWithOption(a, b, c)
  )
  given Arbitrary[NestedClassWithOption] = Arbitrary(Gen.option(Arbitrary.arbitrary[FlatClassWithOption]).map(NestedClassWithOption(_)))
  given Arbitrary[FlatClassWithList] = Arbitrary(
    for (a <- genString; b <- Gen.listOf(Arbitrary.arbitrary[Int])) yield FlatClassWithList(a, b)
  )
  given Arbitrary[NestedClassWithList] = Arbitrary(Gen.listOf(Arbitrary.arbitrary[FlatClassWithList]).map(NestedClassWithList(_)))
  given Arbitrary[SimpleTestResult] = Arbitrary(genString.map(SimpleTestResult(_)))
  given Arbitrary[ClassWithMap] = Arbitrary(Gen.mapOf(Gen.zip(Gen.alphaNumStr, Arbitrary.arbitrary[SimpleTestResult])).map(ClassWithMap(_)))
  given Arbitrary[ClassWithEither] = Arbitrary(
    for (a <- genString; b <- Gen.either(genString, Arbitrary.arbitrary[SimpleTestResult])) yield ClassWithEither(a, b)
  )
  given Arbitrary[ClassWithValues] = Arbitrary(
    for (id <- Gen.uuid; name <- genString; age <- Arbitrary.arbitrary[Int]) yield ClassWithValues(UserId(id), UserName(name), age)
  )
  given Arbitrary[ClassWithScalaDefault] = Arbitrary(for (a <- genString; b <- genString) yield ClassWithScalaDefault(a, b))

  given Arbitrary[ErrorCode] = Arbitrary(
    Gen.oneOf(Gen.const(ErrorNotFound), Gen.const(ErrorTimeout), genString.map(CustomError(_)))
  )
  given Arbitrary[ErrorCodeHolder] = Arbitrary(for (a <- Arbitrary.arbitrary[ErrorCode]; b <- genString) yield ErrorCodeHolder(a, b))
  given Arbitrary[Status] = Arbitrary(
    Gen.oneOf(Arbitrary.arbitrary[Int].map(StatusOk(_)), Arbitrary.arbitrary[Int].map(StatusBadRequest(_)), Gen.const(StatusInternalError))
  )
  given Arbitrary[StatusResponse] = Arbitrary(Arbitrary.arbitrary[Status].map(StatusResponse(_)))
  given Arbitrary[SealedVariant] = Arbitrary(Gen.oneOf(VariantA, VariantB, VariantC))
  given Arbitrary[SealedVariantContainer] = Arbitrary(Arbitrary.arbitrary[SealedVariant].map(SealedVariantContainer(_)))
  given Arbitrary[NotAllSealedVariant] = Arbitrary(
    Gen.oneOf(Gen.const(NotAllSealedVariantA), Arbitrary.arbitrary[Int].map(NotAllSealedVariantB(_)))
  )
  given Arbitrary[ColorEnum] = Arbitrary(Gen.oneOf(ColorEnum.values.toSeq))
  given Arbitrary[Response] = Arbitrary(for (c <- Arbitrary.arbitrary[ColorEnum]; d <- genString) yield Response(c, d))
  given Arbitrary[RichColorEnum] = Arbitrary(Gen.oneOf(RichColorEnum.values.toSeq))
  given Arbitrary[RichColorResponse] = Arbitrary(Arbitrary.arbitrary[RichColorEnum].map(RichColorResponse(_)))
  given Arbitrary[Entity] = Arbitrary(
    Gen.oneOf(
      for (f <- genString; a <- Arbitrary.arbitrary[Int]) yield Entity.Person(f, a),
      genString.map(Entity.Business(_))
    )
  )

  given Arbitrary[Tree] = Arbitrary(Gen.sized { size =>
    def tree(depth: Int): Gen[Tree] =
      for {
        value <- Arbitrary.arbitrary[Int]
        children <- if (depth <= 0) Gen.const(Nil) else Gen.resize(size / 2, Gen.listOf(Gen.lzy(tree(depth - 1))))
      } yield Tree(value, children)
    tree(3)
  })

  given Arbitrary[Node] = Arbitrary {
    def node(depth: Int): Gen[Node] =
      if (depth <= 0) Arbitrary.arbitrary[Long].map(SimpleNode(_))
      else
        Gen.oneOf(
          Arbitrary.arbitrary[Long].map(SimpleNode(_)),
          for (id <- Arbitrary.arbitrary[Long]; s <- Gen.lzy(node(depth - 1))) yield Edge(id, s)
        )
    node(4)
  }

  given Arbitrary[MutualA] = Arbitrary {
    def a(depth: Int): Gen[MutualA] =
      for (id <- Arbitrary.arbitrary[Int]; b <- if (depth <= 0) Gen.const(None) else Gen.option(Gen.lzy(b(depth - 1)))) yield MutualA(b, id)
    def b(depth: Int): Gen[MutualB] =
      for (id <- Arbitrary.arbitrary[Int]; a0 <- if (depth <= 0) Gen.const(None) else Gen.option(Gen.lzy(a(depth - 1))))
        yield MutualB(a0, id)
    a(3)
  }
}
