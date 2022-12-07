package sttp.tapir.serverless.aws.cdk.internal

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.serverless.aws.cdk.internal
import sttp.tapir.serverless.aws.cdk.internal.Segment.Parameter
import sttp.tapir.serverless.aws.cdk.internal.Segment.Fixed

class TreeToTypeScriptTest extends AnyFlatSpec with Matchers with OptionValues {
  implicit class StringSeqSyntax(l: Seq[String]) {
    def format: String = l.mkString(System.lineSeparator())
  }

  it should "return empty list for fixed path with missing methods" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List.empty
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe Seq(
      "const rootApi = api.root.addResource('api');"
    ).format
  }

  it should "generate root api TS for fixed path with one element" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List(internal.Method.GET),
        children = List.empty
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "// GET /api",
      "const rootApi = api.root.addResource('api');",
      "rootApi.addMethod('GET');"
    ).format
  }

  it should "generate TS for fixed path with two element" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List(
          Node(
            name = Fixed("hello").value,
            methods = List(internal.Method.GET)
          )
        )
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "const rootApi = api.root.addResource('api');",
      "",
      "// GET /api/hello",
      "const rootApiHello = rootApi.addResource('hello');",
      "rootApiHello.addMethod('GET');"
    ).format
  }

  // you may ask why 3 ;)
  it should "generate TS for path for path with 3 segments" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List(
          Node(
            name = Fixed("hello").value,
            methods = List(internal.Method.GET),
            children = List(
              Node(
                name = Parameter("name").value,
                methods = List(internal.Method.POST)
              )
            )
          )
        )
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "const rootApi = api.root.addResource('api');",
      "",
      "// GET /api/hello",
      "const rootApiHello = rootApi.addResource('hello');",
      "rootApiHello.addMethod('GET');",
      "",
      "// POST /api/hello/{name}",
      "const rootApiHelloNameParam = rootApiHello.addResource('{name}');",
      "rootApiHelloNameParam.addMethod('POST');"
    ).format
  }

  it should "generate TS for path for only root nodes" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List(internal.Method.GET),
        children = List.empty
      ),
      Node(
        name = Fixed("health-check").value,
        methods = List(internal.Method.GET),
        children = List.empty
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "// GET /api",
      "const rootApi = api.root.addResource('api');",
      "rootApi.addMethod('GET');",
      "",
      "// GET /health-check",
      "const rootHealthcheck = api.root.addResource('health-check');",
      "rootHealthcheck.addMethod('GET');"
    ).format
  }

  it should "generate TS for path for path with middle segment without any methods" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List(
          Node(
            name = Fixed("hello").value,
            methods = List.empty,
            children = List(
              Node(
                name = Parameter("name").value,
                methods = List(internal.Method.PUT)
              )
            )
          )
        )
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "const rootApi = api.root.addResource('api');",
      "",
      "const rootApiHello = rootApi.addResource('hello');",
      "",
      "// PUT /api/hello/{name}",
      "const rootApiHelloNameParam = rootApiHello.addResource('{name}');",
      "rootApiHelloNameParam.addMethod('PUT');"
    ).format
  }

  it should "generate TS for path with multiple methods" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List(internal.Method.GET, internal.Method.PATCH),
        children = List(
          Node(
            name = Fixed("hello").value,
            methods = List.empty,
            children = List(
              Node(
                name = Parameter("id").value,
                methods = List(internal.Method.POST, internal.Method.DELETE)
              )
            )
          )
        )
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "// GET /api",
      "// PATCH /api",
      "const rootApi = api.root.addResource('api');",
      "rootApi.addMethod('GET');",
      "rootApi.addMethod('PATCH');",
      "",
      "const rootApiHello = rootApi.addResource('hello');",
      "",
      "// POST /api/hello/{id}",
      "// DELETE /api/hello/{id}",
      "const rootApiHelloIdParam = rootApiHello.addResource('{id}');",
      "rootApiHelloIdParam.addMethod('POST');",
      "rootApiHelloIdParam.addMethod('DELETE');"
    ).format
  }

  it should "handle parameters with the same raw name" in {
    // given
    val tree = List(
      Node(
        name = Fixed("api").value,
        methods = List.empty,
        children = List(
          Node(
            name = Parameter("id").value,
            children = List(
              Node(name = Fixed("name").value, methods = List(internal.Method.DELETE))
            )
          ),
          Node(
            name = Fixed("id").value,
            children = List(
              Node(name = Parameter("name").value, methods = List(internal.Method.DELETE))
            )
          )
        )
      )
    )

    // when
    val result = TreeToTypeScript.apply(tree)

    // then
    result.format shouldBe List(
      "const rootApi = api.root.addResource('api');",
      "",
      "const rootApiIdParam = rootApi.addResource('{id}');",
      "",
      "// DELETE /api/{id}/name",
      "const rootApiIdParamName = rootApiIdParam.addResource('name');",
      "rootApiIdParamName.addMethod('DELETE');",
      "",
      "const rootApiId = rootApi.addResource('id');",
      "",
      "// DELETE /api/id/{name}",
      "const rootApiIdNameParam = rootApiId.addResource('{name}');",
      "rootApiIdNameParam.addMethod('DELETE');"
    ).format
  }
}
