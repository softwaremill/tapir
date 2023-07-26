package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.{OpenapiComponent, OpenapiSchemaType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.{
  OpenapiDocument,
  OpenapiInfo,
  OpenapiParameter,
  OpenapiPath,
  OpenapiPathMethod,
  OpenapiRequestBody,
  OpenapiRequestBodyContent,
  OpenapiResponse,
  OpenapiResponseContent
}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaDouble,
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
      |    post:
      |      operationId: postBooksGenreYear
      |      parameters:
      |      - name: genre
      |        in: path
      |        required: true
      |        schema:
      |          type: string
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
      |components:
      |  schemas:
      |    Book:
      |      required:
      |      - title
      |      type: object
      |      properties:
      |        title:
      |          type: string
      |""".stripMargin

  val myBookshopDoc = OpenapiDocument(
    "3.1.0",
    OpenapiInfo("My Bookshop", "1.0"),
    Seq(
      OpenapiPath(
        "/books/{genre}/{year}",
        Seq(
          OpenapiPathMethod(
            methodType = "post",
            parameters = Seq(
              OpenapiParameter("genre", "path", true, None, OpenapiSchemaString(false)),
              OpenapiParameter("year", "path", true, None, OpenapiSchemaInt(false)),
              OpenapiParameter("limit", "query", true, Some("Maximum number of books to retrieve"), OpenapiSchemaInt(false)),
              OpenapiParameter("X-Auth-Token", "header", true, None, OpenapiSchemaString(false))
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
            methodType = "get",
            parameters = Seq(
              OpenapiParameter("genre", "path", true, None, OpenapiSchemaString(false)),
              OpenapiParameter("year", "path", true, None, OpenapiSchemaInt(false)),
              OpenapiParameter("limit", "query", true, Some("Maximum number of books to retrieve"), OpenapiSchemaInt(false)),
              OpenapiParameter("X-Auth-Token", "header", true, None, OpenapiSchemaString(false))
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
          )
        )
      )
    ),
    Some(OpenapiComponent(
      Map(
        "Book" -> OpenapiSchemaObject(Map("title" -> OpenapiSchemaString(false)), Seq("title"), false)
      )
    ))
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
              OpenapiParameter("name", "query", true, None, OpenapiSchemaString(false))
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
                content = List(OpenapiResponseContent("application/json", OpenapiSchemaArray(OpenapiSchemaRef("#/components/schemas/Book"), false)))
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
    Some(OpenapiComponent(
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
    ))
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
              OpenapiParameter("name", "path", true, None, OpenapiSchemaString(false))
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
            tags = None
          )
        )
      )
    ),
    None
  )
}