package sttp.tapir.generic

import java.math.{BigDecimal => JBigDecimal}

import com.github.ghik.silencer.silent
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.SchemaType._
import sttp.tapir.{SchemaType, Schema}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@silent("never used")
class SchemaGenericMultiThreadTest extends FlatSpec with Matchers {
  private val stringSchema = implicitly[Schema[String]]
  private val intSchema = implicitly[Schema[Int]]
  private val longSchema = implicitly[Schema[Long]]

  it should "find schema for recursive data structure when invoked from many threads" in {
    val expected =
      SProduct(
        SObjectInfo("sttp.tapir.generic.F"),
        List(("f1", Schema(SRef(SObjectInfo("sttp.tapir.generic.F"))).asArrayElement), ("f2", intSchema))
      )

    val count = 100
    val futures = (1 until count).map { _ =>
      Future[SchemaType] {
        implicitly[Schema[F]].schemaType
      }
    }

    val eventualSchemas = Future.sequence(futures)

    val schemas = Await.result(eventualSchemas, 5 seconds)
    schemas should contain only expected
  }

}

case class StringValueClass(value: String) extends AnyVal
case class IntegerValueClass(value: Int) extends AnyVal

case class A(f1: String, f2: Int, f3: Option[String])
case class B(g1: String, g2: A)
case class C(h1: List[String], h2: Option[Int])
case class D(someFieldName: String)
case class F(f1: List[F], f2: Int)

@silent("never used")
class Custom(c: String)
case class G(f1: Int, f2: Custom)

case class H[T](data: T)

sealed trait Node
case class Edge(id: Long, source: Node) extends Node
case class SimpleNode(id: Long) extends Node

case class IOpt(i1: Option[IOpt], i2: Int)
case class JOpt(data: Option[IOpt])

case class IList(i1: List[IList], i2: Int)
case class JList(data: List[IList])
