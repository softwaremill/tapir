package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiComponent
import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiInfo,
  OpenapiParameter,
  OpenapiPath,
  OpenapiPathMethod,
  OpenapiRequestBody,
  OpenapiRequestBodyContent,
  OpenapiResponse,
  OpenapiResponseContent,
  Ref,
  Resolved
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaConstantString,
  OpenapiSchemaEnum,
  OpenapiSchemaInt,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaString
}

object TestHelpers {

  val myBookshopYaml =
    """
      |openapi: 3.1.0
      |info:
      |  title: My Bookshop
      |  version: '1.0'
      |paths:
      |  /books/{genre}/{year}:
      |    parameters:
      |    - name: genre
      |      in: path
      |      schema:
      |        type: string
      |    - $ref: '#/components/parameters/year'
      |    post:
      |      operationId: postBooksGenreYear
      |      parameters:
      |      - name: year
      |        in: path
      |        required: true
      |        schema:
      |          type: integer
      |      - name: limit
      |        in: query
      |        description: Maximum number of books to retrieve
      |        required: true
      |        schema:
      |          type: integer
      |      - name: X-Auth-Token
      |        in: header
      |        required: true
      |        schema:
      |          type: string
      |      requestBody:
      |        description: Book to add
      |        required: true
      |        content:
      |          application/json:
      |            schema:
      |              $ref: '#/components/schemas/Book'
      |      responses:
      |        '200':
      |          description: ''
      |          content:
      |            application/json:
      |              schema:
      |                type: array
      |                items:
      |                  $ref: '#/components/schemas/Book'
      |      tags:
      |        - Bookshop
      |    get:
      |      operationId: getBooksGenreYear
      |      parameters:
      |      - name: genre
      |        in: path
      |        required: true
      |        schema:
      |          type: string
      |      - $ref: '#/components/parameters/offset'
      |      - name: limit
      |        in: query
      |        description: Maximum number of books to retrieve
      |        required: true
      |        schema:
      |          type: integer
      |      - name: X-Auth-Token
      |        in: header
      |        required: true
      |        schema:
      |          type: string
      |      responses:
      |        '200':
      |          description: ''
      |          content:
      |            application/json:
      |              schema:
      |                type: array
      |                items:
      |                  $ref: '#/components/schemas/Book'
      |        default:
      |          description: ''
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |      tags:
      |        - Bookshop
      |    delete:
      |      operationId: deleteBooksGenreYear
      |      responses:
      |        '200':
      |          description: 'deletion was successful'
      |        default:
      |          description: 'deletion failed'
      |      tags:
      |        - Bookshop
      |components:
      |  schemas:
      |    Book:
      |      required:
      |      - title
      |      type: object
      |      properties:
      |        title:
      |          type: string
      |  parameters:
      |    offset:
      |      name: offset
      |      in: query
      |      description: Offset at which to start fetching books
      |      required: true
      |      schema:
      |        type: integer
      |    year:
      |      name: year
      |      in: path
      |      required: true
      |      schema:
      |        type: integer
      |""".stripMargin

  val myBookshopDoc = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("My Bookshop", "1.0"),
    Seq(
      OpenapiPath(
        "/books/{genre}/{year}",
        Seq(
          OpenapiPathMethod(
            methodType = "get",
            parameters = Seq(
              Resolved(OpenapiParameter("genre", "path", Some(true), None, OpenapiSchemaString(false))),
              Ref[OpenapiParameter]("#/components/parameters/offset"),
              Resolved(
                OpenapiParameter("limit", "query", Some(true), Some("Maximum number of books to retrieve"), OpenapiSchemaInt(false))
              ),
              Resolved(OpenapiParameter("X-Auth-Token", "header", Some(true), None, OpenapiSchemaString(false)))
            ),
            responses = Seq(
              OpenapiResponse(
                "200",
                "",
                Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
              ),
              OpenapiResponse("default", "", Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false))))
            ),
            requestBody = None,
            summary = None,
            tags = Some(Seq("Bookshop")),
            operationId = Some("getBooksGenreYear")
          ),
          OpenapiPathMethod(
            methodType = "post",
            parameters = Seq(
              Resolved(OpenapiParameter("year", "path", Some(true), None, OpenapiSchemaInt(false))),
              Resolved(
                OpenapiParameter("limit", "query", Some(true), Some("Maximum number of books to retrieve"), OpenapiSchemaInt(false))
              ),
              Resolved(OpenapiParameter("X-Auth-Token", "header", Some(true), None, OpenapiSchemaString(false)))
            ),
            responses = Seq(
              OpenapiResponse(
                "200",
                "",
                Seq(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
              )
            ),
            requestBody = Option(
              OpenapiRequestBody(
                required = true,
                content = Seq(
                  OpenapiRequestBodyContent(
                    "application/json",
                    OpenapiSchemaRef("#/components/schemas/Book")
                  )
                ),
                description = Some("Book to add")
              )
            ),
            summary = None,
            tags = Some(Seq("Bookshop")),
            operationId = Some("postBooksGenreYear")
          ),
          OpenapiPathMethod(
            methodType = "delete",
            parameters = Nil,
            responses = Seq(
              OpenapiResponse("200", "deletion was successful", Nil),
              OpenapiResponse("default", "deletion failed", Nil)
            ),
            requestBody = None,
            summary = None,
            tags = Some(Seq("Bookshop")),
            operationId = Some("deleteBooksGenreYear")
          )
        ),
        parameters = Seq(
          Resolved(OpenapiParameter("genre", "path", None, None, OpenapiSchemaString(false))),
          Ref("#/components/parameters/year")
        )
      )
    ),
    Some(
      OpenapiComponent(
        schemas = Map(
          "Book" -> OpenapiSchemaObject(Map("title" -> OpenapiSchemaString(false)), Seq("title"), false)
        ),
        securitySchemes = Map.empty,
        parameters = Map(
          "#/components/parameters/offset" ->
            OpenapiParameter("offset", "query", Some(true), Some("Offset at which to start fetching books"), OpenapiSchemaInt(false)),
          "#/components/parameters/year" -> OpenapiParameter("year", "path", Some(true), None, OpenapiSchemaInt(false))
        )
      )
    )
  )

  val generatedBookshopYaml =
    """
      |openapi: 3.1.0
      |info:
      |  title: Generated Bookshop
      |  version: '1.0'
      |paths:
      |  /hello:
      |    get:
      |      operationId: getHello
      |      parameters:
      |      - name: name
      |        in: query
      |        required: true
      |        schema:
      |          type: string
      |      responses:
      |        '200':
      |          description: ''
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |        '400':
      |          description: 'Invalid value for: query parameter name'
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |  /books/list/all:
      |    get:
      |      operationId: getBooksListAll
      |      responses:
      |        '200':
      |          description: ''
      |          content:
      |            application/json:
      |              schema:
      |                type: array
      |                items:
      |                  $ref: '#/components/schemas/Book'
      |components:
      |  schemas:
      |    Author:
      |      required:
      |      - name
      |      type: object
      |      properties:
      |        name:
      |          type: string
      |    Book:
      |      required:
      |      - title
      |      - year
      |      - author
      |      type: object
      |      properties:
      |        title:
      |          type: string
      |        year:
      |          type: integer
      |          format: int32
      |        author:
      |          $ref: '#/components/schemas/Author'
      |""".stripMargin

  val generatedBookshopDoc = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("Generated Bookshop", "1.0"),
    Seq(
      OpenapiPath(
        url = "/hello",
        methods = Seq(
          OpenapiPathMethod(
            methodType = "get",
            parameters = Seq(
              Resolved(OpenapiParameter("name", "query", Some(true), None, OpenapiSchemaString(false)))
            ),
            responses = Seq(
              OpenapiResponse(
                "200",
                "",
                Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false)))
              ),
              OpenapiResponse(
                code = "400",
                description = "Invalid value for: query parameter name",
                content = Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false)))
              )
            ),
            requestBody = None,
            summary = None,
            tags = None,
            operationId = Some("getHello")
          )
        )
      ),
      OpenapiPath(
        url = "/books/list/all",
        methods = Seq(
          OpenapiPathMethod(
            methodType = "get",
            parameters = Seq(),
            responses = Seq(
              OpenapiResponse(
                code = "200",
                description = "",
                content =
                  List(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
              )
            ),
            requestBody = None,
            summary = None,
            tags = None,
            operationId = Some("getBooksListAll")
          )
        )
      )
    ),
    Some(
      OpenapiComponent(
        Map(
          "Author" -> OpenapiSchemaObject(Map("name" -> OpenapiSchemaString(false)), List("name"), false),
          "Book" -> OpenapiSchemaObject(
            properties = Map(
              "title" -> OpenapiSchemaString(false),
              "year" -> OpenapiSchemaInt(false),
              "author" -> OpenapiSchemaRef("#/components/schemas/Author")
            ),
            required = Seq("title", "year", "author"),
            nullable = false
          )
        )
      )
    )
  )

  val helloYaml =
    """openapi: 3.1.0
      |info:
      |  title: hello
      |  version: 1.0.0
      |paths:
      |  /hello:
      |    get:
      |      operationId: getHello
      |      parameters:
      |      - name: name
      |        in: path
      |        required: true
      |        schema:
      |          type: string
      |      responses:
      |        '200':
      |          description: ''
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |        '400':
      |          description: 'Invalid value for: query parameter name'
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |""".stripMargin

  val helloDocs = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("hello", "1.0.0"),
    Seq(
      OpenapiPath(
        url = "/hello",
        methods = Seq(
          OpenapiPathMethod(
            methodType = "get",
            Seq(
              Resolved(OpenapiParameter("name", "path", Some(true), None, OpenapiSchemaString(false)))
            ),
            responses = Seq(
              OpenapiResponse(
                "200",
                "",
                Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false)))
              ),
              OpenapiResponse(
                code = "400",
                description = "Invalid value for: query parameter name",
                content = Seq(OpenapiResponseContent("text/plain", OpenapiSchemaString(false)))
              )
            ),
            requestBody = None,
            summary = None,
            tags = None,
            operationId = Some("getHello")
          )
        )
      )
    ),
    None
  )

  val simpleSecurityYaml =
    """
      |openapi: 3.1.0
      |info:
      |  title: hello
      |  version: '1.0'
      |paths:
      |  /hello:
      |    get:
      |      security:
      |        - basicAuth: []
      |      responses: {}
    """.stripMargin

  val simpleSecurityDocs = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("hello", "1.0"),
    Seq(
      OpenapiPath(
        url = "/hello",
        methods = Seq(
          OpenapiPathMethod(
            methodType = "get",
            parameters = Seq(),
            responses = Seq(),
            requestBody = None,
            security = Seq(Seq("basicAuth"))
          )
        )
      )
    ),
    None
  )

  val complexSecurityYaml =
    """
      |openapi: 3.1.0
      |info:
      |  title: hello
      |  version: '1.0'
      |paths:
      |  /hello:
      |    get:
      |      security:
      |        - bearerAuth: []
      |        - basicAuth: []
      |          apiKeyAuth: []
      |      responses: {}
    """.stripMargin

  val complexSecurityDocs = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("hello", "1.0"),
    Seq(
      OpenapiPath(
        url = "/hello",
        methods = Seq(
          OpenapiPathMethod(
            methodType = "get",
            parameters = Seq(),
            responses = Seq(),
            requestBody = None,
            security = Seq(Seq("bearerAuth"), Seq("basicAuth", "apiKeyAuth"))
          )
        )
      )
    ),
    None
  )

  val enumQueryParamYaml =
    """
      |openapi: 3.1.0
      |info:
      |  title: enum query test
      |  version: '1.0'
      |paths:
      |  /queryTest:
      |    parameters:
      |      - name: test
      |        in: query
      |        required: false
      |        schema:
      |          $ref: '#/components/schemas/Test'
      |    post:
      |      responses: {}
      |
      |components:
      |  schemas:
      |    Test:
      |      title: Test
      |      type: string
      |      enum:
      |        - paperback
      |        - hardback
      |""".stripMargin

  val enumQueryParamDocs = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("enum query test", "1.0"),
    Seq(
      OpenapiPath(
        "/queryTest",
        Seq(
          OpenapiPathMethod(
            methodType = "post",
            parameters = Seq(),
            responses = Seq(),
            requestBody = None,
            summary = None,
            tags = None,
            operationId = None
          )
        ),
        parameters = Seq(
          Resolved(OpenapiParameter("test", "query", None, None, OpenapiSchemaRef("#/components/schemas/Test")))
        )
      )
    ),
    Some(
      OpenapiComponent(
        Map(
          "Test" -> OpenapiSchemaEnum(
            "string",
            Seq(OpenapiSchemaConstantString("paperback"), OpenapiSchemaConstantString("hardback")),
            false
          )
        )
      )
    )
  )
}
