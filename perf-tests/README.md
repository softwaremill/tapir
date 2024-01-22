# Performance tests

Performance tests are executed by running `PerfTestSuiteRunner`, which is a standard "Main" Scala application, configured by command line parameters. It executes a sequence of tests, where
each test consist of:

1. Starting a HTTP server (Like Tapir-based Pekko, Vartx, http4s, or a "vanilla", tapirless one)
2. Sending a bunch of warmup requests
3. Sending simulation-specific requests
4. Closing the server

The sequence is repeated for a set of servers multiplied by simulations, all configurable as arguments. Command parameters can be viewed by running:

```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner
```

which displays help similar to:

```
[error] Usage: perf [options]
[error]   -s, --server <value>    Comma-separated list of short server names, or '*' for all. Available servers: http4s.TapirMulti, http4s.Tapir, http4s.VanillaMulti, http4s.Vanilla, 
netty.cats.TapirMulti, netty.cats.Tapir, netty.future.TapirMulti, netty.future.Tapir, pekko.TapirMulti, pekko.Tapir, pekko.VanillaMulti, pekko.Vanilla, play.TapirMulti, play.Tapir,
play.VanillaMulti, play.Vanilla, vertx.TapirMulti, vertx.Tapir, vertx.VanillaMulti, vertx.Vanilla, vertx.cats.TapirMulti, vertx.cats.Tapir
[error]   -m, --sim <value>       Comma-separated list of short simulation names, or '*' for all. Available simulations: PostBytes, PostFile, PostLongBytes, PostLongString, 
PostString, SimpleGetMultiRoute, SimpleGet
[error]   -u, --users <value>     Number of concurrent users, default is 1
[error]   -d, --duration <value>  Single simulation duration in seconds, default is 10
[error]   -g, --gatling-reports   Generate Gatling reports for individuals sims, may significantly affect total time (disabled by default)
```

## Examples

1. Run all sims on all servers with other options set to default (Careful, may take quite some time!):
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s * -m *
```

2. Run all sims on http4s servers, with each simulation running for 5 seconds:
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s http4s.Tapir,http4s.TapirMulti,http4s.Vanilla,http4s.VanillaMulti -s * -d 5
```

3. Run some simulations on some servers, with 3 concurrent users instead of default 1, each simulation running for 15 seconds, 
and enabled Gatling report generation:
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s http4s.Tapir,netty.future.Tapir,play.Tapir -s PostLongBytes,PostFile -d 15 -u 3 -g
```

## Reports

After all tests finish successfully, your console output will point to report files, 
containing aggregated results from the entire suite:
```
[info] ******* Test Suite report saved to /home/kc/code/oss/tapir/.sbt/matrix/perfTests/tapir-perf-tests-2024-01-22_16_33_14.csv
[info] ******* Test Suite report saved to /home/kc/code/oss/tapir/.sbt/matrix/perfTests/tapir-perf-tests-2024-01-22_16_33_14.html
```

These reports include information about throughput and latency of each server for each simulation.

How the aggregation works: After each test the results are read from `simulation.log` produced by Gatling and aggregated by `GatlingLogProcessor`. 
Entires related to warm-up process are not counted. The processor then uses 'com.codehale.metrics.Histogram' to calculate 
p99, p95, p75, and p50 percentiles for latencies of all requests sent during the simulation.

## Adding new servers and simulations

To add a new server, go to `src/main/scala` and put an object extending `sttp.tapir.perf.apis.ServerRunner` in a subpackage of `sttp.tapir.perf`. 
It should be automatically resoled by the `TypeScanner` utility used by the `PerfTestSuiteRunner`.

Similarly with simulations. Go to `src/test/scala` and a class extending `io.gatling.core.Predef.Simulation` under `sttp.tapir.perf`. See the `Simulations.scala` 
file for examples.
