# Other interpreters

At its core, tapir creates a data structure describing the HTTP endpoints. This data structure can be freely 
interpreted also by code not included in the library. Below is a list of projects, which provide tapir interpreters.

## tapir-gen

[tapir-gen](https://github.com/xplosunn/tapir-gen) extends tapir to do client code generation. The goal is to 
auto-generate clients in multiple-languages with multiple libraries.

[scala-opentracing](https://github.com/Colisweb/scala-opentracing) contains a module which provides a small integration 
layer that allows you to create traced http endpoints from tapir Endpoint definitions.
