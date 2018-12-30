package tapir.tests

import tapir._
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import io.circe.generic.auto._
import tapir.json.circe._

object Tests2 extends App {
  case class Address(street: String, number: Option[Int])
  case class User(first: String, age: Int, address: Address)

  val e = endpoint.get
    .in("x" / path[String]("p1") / "z" / path[Int]("p2")) // each endpoint must have a path and a method
    .in(query[String]("q1").description("A q1").and(query[Int]("q2").example(99)))
    .in(query[Option[String]]("q3"))
    .out(jsonBody[User].example(User("x", 10, Address("y", Some(20)))))

  val docs = e.toOpenAPI("Example 1", "1.0")
  println(docs.toYaml)
}

/*
TODO:
 * human-friendly type errors
 * 404 test
 * status codes
 * error-result tests
 */

/*

Body:

basic:

- string                body[String]
- array[byte]           body[Array[Byte]]
- byte buffer           body[ByteBuffer]
- input stream          body[InputStream]
- file                  body[File]           save request body/response to file -> temporary? (file creation strategy?); send the given file
- map[string, string]   body[Seq[(String, String)]], body[Map[String, String]]  urlencoded!
- stream (*)            streamBody[...]
- multipart:
  - any basic

inputs
- all query params                 allQueryParams: Seq[(String, String)]
- all headers (as map)             allHeaders: Seq[(String, String)]
- remaining path components        remainingPath: List[String]

- all form parameters - FROM BODY! body -> Seq[(String, String)]

- query param                      query[String]("y")                   queryParam
- path constant/capture            path[String]                         pathSegment
- header                           header[String]("x")

- form parameter - FROM BODY!      form[String]("z")                    mandates urlencoded form body

- one multipart part               multipart[String]("a") -> Part[String] with headers, filename, body, name
- all multipart parts              allMultipartParts: Seq[Part] (StringPart / BinaryPart)


endpoint compile / non-compile tests
 */
