# Other interpreters & extensions

At its core, Tapir creates a data structure describing the HTTP endpoints. This data structure can be freely 
interpreted also by code not included in the library. Below is a list of projects, which provide tapir interpreters
and extensions to the project.

## GraphQL

[Caliban](https://github.com/ghostdogpr/caliban) allows you to easily turn your Tapir endpoints into a GraphQL API. More details in the [documentation](https://ghostdogpr.github.io/caliban/docs/interop.html#tapir).

## tapir-gen

[tapir-gen](https://github.com/xplosunn/tapir-gen) extends tapir to do client code generation. The goal is to 
auto-generate clients in multiple-languages with multiple libraries.

[scala-opentracing](https://github.com/Colisweb/scala-opentracing) contains a module which provides a small integration 
layer that allows you to create traced http endpoints from tapir Endpoint definitions.

## SNUnit

[SNUnit](https://github.com/lolgab/snunit) is a Scala Native HTTP Server library based on [NGINX Unit](https://unit.nginx.org/). It provides first-class support for Tapir.

## tapir-http-session

[tapir-http-session](https://github.com/SOFTNETWORK-APP/tapir-http-session) provides integration with functionality of [akka-http-session](https://github.com/softwaremill/akka-http-session), which includes client-side session management in web and mobile applications.

## tapir + kyo

[Kyo](https://github.com/getkyo/kyo/#routes-http-server-via-tapir) includes a tapir integration module.

## Baku

[Baku](https://github.com/arkida39/baku) is a Tapir extension library that allows you to easily isolate your API definitions from server and security logic for cleaner, more maintainable code. This makes it simple to share contracts across microservices without exposing the underlying implementation.