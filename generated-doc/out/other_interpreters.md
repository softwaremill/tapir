# Other integrations

At its core, tapir creates a data structure describing the HTTP endpoints. This data structure can be freely 
interpreted by other libraries and frameworks. Below is a list of projects that provide tapir integrations.

## GraphQL

[Caliban](https://github.com/ghostdogpr/caliban) allows you to easily turn your Tapir endpoints into a GraphQL API. More details in the [documentation](https://ghostdogpr.github.io/caliban/docs/interop.html#tapir).

## Scala Opentracing (Http4s)

[scala-opentracing](https://github.com/Colisweb/scala-opentracing) contains a module which provides a small integration layer that allows you to create traced http endpoints from tapir Endpoint definitions.
