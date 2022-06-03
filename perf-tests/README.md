# seperate testing

To start a server, run `perfTests/run` and select the server you want to test, or in a single command:

```
sbt "perfTests/runMain sttp.tapir.perf.akka.VanillaMultiServer"
// or
sbt "perfTests/runMain sttp.tapir.perf.akka.TapirMultiServer"
// or others ...
```

Then run the test:
```
sbt "perfTests/Gatling/testOnly sttp.tapir.perf.OneRouteSimulation"
// or
sbt "perfTests/Gatling/testOnly sttp.tapir.perf.MultiRouteSimulation"
```

This method yields the most performant results, but requires running the commands in two seperate sbt instacnes.

# single-command testing

To run the server together with a test, simply:

```
perfTests/akkaHttpVanilla
```
or
```
perfTests/akkaHttpTapir
```

Servers under this method are slightly less performant, but do not need to be run from seperate terminals. The performance loss doesn't seem to affect the relative performance of different servers.

