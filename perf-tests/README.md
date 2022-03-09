# seperate testing

To start a server, run `perfTests/run` and select the server you want to test.

To test vanilla akka-http load, run this command:
```
perfTests / Gatling / testOnly perfTests.AkkaHttpVanillaSimulation
```

To test tapir with akka-http load, run this command:
```
perfTests / Gatling / testOnly perfTests.AkkaHttpTapirSimulation
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

