# Examples

The [examples](https://github.com/softwaremill/tapir/tree/master/examples/src/main/scala/sttp/tapir/examples) and [examples2](https://github.com/softwaremill/tapir/tree/master/examples2/src/main/scala/sttp/tapir/examples2) sub-projects (the latter containing Scala 2-only code) contains a number of runnable tapir usage examples, using various interpreters and showcasing different features.

## Generate a tapir project

You can generate a simple tapir-based project using chosen features, build tool and effect system using [adopt-tapir](https://adopt-tapir.softwaremill.com).

Alternatively, you can generate a stub of a tapir-based application directly from the command line with `sbt new softwaremill/tapir.g8`.

## Third-party examples

* http4s interpreter: [todo-backend](https://github.com/lolgab/snunit-tapir-example)
* quickstart using http4s: [a gitter8 template](https://codeberg.org/wegtam/http4s-tapir.g8). A new project can be created using: `sbt new https://codeberg.org/wegtam/http4s-tapir.g8.git`
* Scala Native application, [using Nginx Unit](https://github.com/lolgab/snunit-tapir-example).

## Blogs, articles

* [Migrating from Akka HTTP to tapir](https://softwaremill.com/migrating-from-akka-http-to-tapir/)
* [Benchmarking Tapir: Part 1](https://softwaremill.com/benchmarking-tapir-part-1/)
* [Benchmarking Tapir: Part 2](https://softwaremill.com/benchmarking-tapir-part-2/)
* [Tapir 1.0 released](https://softwaremill.com/tapir-1-0-released/)
* [Security improvements in tapir 0.19](https://softwaremill.com/security-improvements-in-tapir-0-19/)
* [Tapir serverless: a proof of concept](https://blog.softwaremill.com/tapir-serverless-a-proof-of-concept-6b8c9de4d396)
* [Designing tapir's WebSockets support](https://blog.softwaremill.com/designing-tapirs-websockets-support-ff1573166368)
* [Three easy endpoints](https://blog.softwaremill.com/three-easy-endpoints-a6cbd52b0a6e)
* [tAPIr's Endpoint meets ZIO's IO](https://blog.softwaremill.com/tapirs-endpoint-meets-zio-s-io-3278099c5e10)
* [Describe, then interpret: HTTP endpoints using tapir](https://blog.softwaremill.com/describe-then-interpret-http-endpoints-using-tapir-ac139ba565b0)
* [Functional pancakes](https://blog.softwaremill.com/functional-pancakes-cf70023f0dcb)

## Videos

* [ScalaLove 2020: Your HTTP endpoints are data, as well!](https://www.youtube.com/watch?v=yuQNgZgSFIc&t=944s)
* [Scalar 2020: A Functional Scala Stack For 2020](https://www.youtube.com/watch?v=DGlkap5kzGU)
* [ScalaWorld 2019: Designing Programmer-Friendly APIs](https://www.youtube.com/watch?v=I3loMuHnYqw)
