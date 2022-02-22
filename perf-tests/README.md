To test vanilla akka-http load, run these two commanda in two seperate `sbt` instances:
`perfServerAkkaHttpOnly/run`
`perfTests / Gatling / testOnly perfTests.AkkaHttpOnlySimulation`

To test tapir with akka-http load, run these two commanda in two seperate `sbt` instances:
`perfServerAkkaHttpTapir/run`
`perfTests / Gatling / testOnly perfTests.AkkaHttpTapirSimulation`





Here are sample results for vanilla akka-http:
| request count                 |  200  |
|-------------------------------|-------|
| min response time             |   1ms |
| max response time             | 307ms |
| mean response time            | 116ms |
| std deviation                 | 117ms |
| response time 50th percentile |  78ms |
| response time 75th percentile | 231ms |
| response time 95th percentile | 287ms |
| response time 99th percentile | 303ms |
| mean requests/sec             | 100ms |



Here are sample results for tapir:
| request count                 |  200  |
|-------------------------------|-------|
| min response time             |   2ms |
| max response time             | 279ms |
| mean response time            |  80ms |
| std deviation                 |  87ms |
| response time 50th percentile |  23ms |
| response time 75th percentile | 162ms |
| response time 95th percentile | 231ms |
| response time 99th percentile | 267ms |
| mean requests/sec             | 100ms |
