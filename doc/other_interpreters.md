# Other interpreters

At its core, tapir creates a data structure describing the HTTP endpoints. This data structure can be freely 
interpreted also by code not included in the library. Below is a list of projects, which provide tapir interpreters.

## tapir-loom

The [tapir-loom](https://github.com/softwaremill/tapir-loom) project provides server interpreters which allow writing the server logic in the "direct" style (synchronous, using the `Id` effect). Depends on Java 19, which includes a preview of Project Loom (Virtual Threads for the JVM).  

## GraphQL

[Caliban](https://github.com/ghostdogpr/caliban) allows you to easily turn your Tapir endpoints into a GraphQL API. More details in the [documentation](https://ghostdogpr.github.io/caliban/docs/interop.html#tapir).

## tapir-gen

[tapir-gen](https://github.com/xplosunn/tapir-gen) extends tapir to do client code generation. The goal is to 
auto-generate clients in multiple-languages with multiple libraries.

[scala-opentracing](https://github.com/Colisweb/scala-opentracing) contains a module which provides a small integration 
layer that allows you to create traced http endpoints from tapir Endpoint definitions.

## SNUnit

[SNUnit](https://github.com/lolgab/snunit) is a Scala Native HTTP Server library based on [NGINX Unit](https://unit.nginx.org/). It provides first-class support for Tapir.
