# SoftwareMill launches stable release of tapir, making it easier than ever for HTTP API developers to benefit from Scala's expressivity

**Warsaw, Poland – June, 2022 – SoftwareMill S.A.**

After almost 4 years of development and multiple 0.x releases, SoftwareMill is happy to announce the release of tapir 1.0! The goal of the [tapir library](https://tapir.softwaremill.com/) is to provide a programmer-friendly, reasonably type-safe API to expose, consume and document HTTP endpoints, using the Scala language.

> “With tapir, you can describe an endpoint as an immutable Scala value. This description and its parts can be arbitrarily extended and reused, making it possible to leverage the abstraction capabilities given by the Scala language.” says Adam Warski, CTO at SoftwareMill. “This is in contrast to the more "traditional" annotation-driven HTTP frameworks, which are severely constrained when it comes to even simple refactorings, such as extracting common code.”

Such a description - capturing the complete metadata of the endpoint - can be then interpreted in three basic ways: as a server, a client, or as documentation.

## Fitted into Scala ecosystem

Any of the leading Scala programming styles, that is `Future`-based, using cats-effect or ZIO can be used. Tapir integrates with a number of existing server implementations, such as akka-http, http4s, vertx or the Play framework.

When generating documentation, tapir can generate “raw” OpenAPI yaml files, and expose them using the SwaggerUI or Redoc. AsyncAPI is supported out-of-the-box as well. Further integrations include observability tools, which take advantage of the tapir-provided metadata, to enrich metrics, logs or traces.

## Ready to facilitate smooth collaboration

Compile-time verification combined with type-driven auto-complete in the IDE allows shortening of feedback loop cycles during development. On the other hand, leveraging the rich endpoint metadata, which is available as an ordinary Scala value, allows smooth collaboration with other teams who want to consume the API being developed, or provide other APIs for consumption.

## Get started now

If you’d like to try tapir, check out our [documentation](https://tapir.softwaremill.com/en/latest/) or the [adopt-tapir page](https://adopt-tapir.softwaremill.com), where you can generate a customised bare-bones tapir project. You can also generate a stub of a tapir-based application directly from the command line with `sbt new softwaremill/tapir.g8` (sbt 1.9+). Looking forward to learning about your impressions of the library!

## Additional Resources

* [scala.page](https://softwaremill.com/scala/)
* [SoftwareMill Tech Blog](https://softwaremill.com/blog/)
* [Scala Times](https://scalatimes.com/)

## About SoftwareMill

We help clients scale their business through software, conduct digital transformation, implement event sourcing and create data processing pipelines. We specialise in Scala, Kafka, Akka and Cassandra, among other technologies. Our areas of expertise include distributed systems, big data, blockchain, machine learning and data analytics. Project in trouble? We'll help your team.
