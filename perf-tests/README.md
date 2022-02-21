To test vanilla akka-http load, run these two commanda in two seperate `sbt` instances:
`perfServerAkkaHttpOnly/run`
`perfTests / Gatling / testOnly perfTests.AkkaHttpOnlySimulation`

To test tapir with akka-http load, run these two commanda in two seperate `sbt` instances:
`perfServerAkkaHttpTapir/run`
`perfTests / Gatling / testOnly perfTests.AkkaHttpTapirSimulation`
