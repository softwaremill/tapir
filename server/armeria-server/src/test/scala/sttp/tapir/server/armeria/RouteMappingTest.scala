package sttp.tapir.server.armeria

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.tests.data.Person
import sttp.tapir.{CodecFormat, Schema, endpoint, fileBody, multipartBody, streamBody, stringBody, stringToPath}

class RouteMappingTest extends AnyFunSuite with Matchers {

  test("unary - stringBody") {
    val stringEndpoint = endpoint
      .in("foo" / "bar")
      .in(stringBody)
      .out(stringBody)
    val routesMap = RouteMapping.toRoute(stringEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.Unary) shouldBe true
  }

  test("unary - json") {
    val stringEndpoint = endpoint
      .in("foo" / "bar")
      .in(jsonBody[Person])
      .out(jsonBody[Person])
    val routesMap = RouteMapping.toRoute(stringEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.Unary) shouldBe true
  }

  test("streaming - fileBody") {
    val stringEndpoint = endpoint
      .in("foo" / "bar")
      .in(fileBody)
      .out(fileBody)
    val routesMap = RouteMapping.toRoute(stringEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.BidiStreaming) shouldBe true
  }

  test("streaming - multipart") {
    val stringEndpoint = endpoint
      .in("foo" / "bar")
      .in(multipartBody)
      .out(multipartBody)
    val routesMap = RouteMapping.toRoute(stringEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.BidiStreaming) shouldBe true
  }

  test("streaming - publisher") {
    val stringEndpoint = endpoint
      .in("foo" / "bar")
      .in(streamBody(ArmeriaStreams)(Schema.derived[List[Person]], CodecFormat.Json()))
      .out(streamBody(ArmeriaStreams)(Schema.derived[List[Person]], CodecFormat.Json()))
    val routesMap = RouteMapping.toRoute(stringEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.BidiStreaming) shouldBe true
  }

  test("mixed - string~file") {
    val stringFileEndpoint = endpoint
      .in("foo" / "bar")
      .in(stringBody)
      .out(fileBody)
    val routesMap = RouteMapping.toRoute(stringFileEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.ResponseStreaming) shouldBe true
  }

  test("mixed - file~string") {
    val fileStringEndpoint = endpoint
      .in("foo" / "bar")
      .in(fileBody)
      .out(stringBody)
    val routesMap = RouteMapping.toRoute(fileStringEndpoint).toMap
    val exchangeTypes = routesMap.values
    exchangeTypes.forall(exchangeType => exchangeType == ExchangeType.RequestStreaming) shouldBe true
  }
}
