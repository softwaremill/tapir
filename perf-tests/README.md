# Performance tests

Performance tests are executed by running `PerfTestSuiteRunner`, which is a standard "Main" Scala application, configured by command line parameters. It executes a sequence of tests, where
each test consist of:

1. Starting a HTTP server (Like Tapir-based Pekko, Vartx, http4s, or a "vanilla", tapirless one)
2. Running a simulation in warm-up mode (5 seconds, 3 concurrent users)
3. Running a simulation with user-defined duration and concurrent user count
4. Closing the server
5. Reading Gatling's simulation.log and building simulation results

The sequence is repeated for a set of servers multiplied by simulations. Afterwards, all individual simulation results will be aggregated into a single report. 
Command parameters can be viewed by running:

```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner
```

which displays help similar to:

```
[error] Usage: perf [options]
[error]   -s, --server <value>    Comma-separated list of short server names, or groups like 'netty.*', 'pekko.*', etc. Available servers: http4s.TapirInterceptorMulti, http4s.TapirMulti, http4s.Tapir, http4s.VanillaMulti, http4s.Vanilla, netty.cats.TapirInterceptorMulti, netty.cats.TapirMulti, netty.cats.Tapir, netty.future.TapirInterceptorMulti, netty.future.TapirMulti, netty.future.Tapir, pekko.TapirInterceptorMulti, pekko.TapirMulti, pekko.Tapir, pekko.VanillaMulti, pekko.Vanilla, play.TapirInterceptorMulti, play.TapirMulti, play.Tapir, play.VanillaMulti, play.Vanilla, vertx.TapirInterceptorMulti, vertx.TapirMulti, vertx.Tapir, vertx.VanillaMulti, vertx.Vanilla, vertx.cats.TapirInterceptorMulti, vertx.cats.TapirMulti, vertx.cats.Tapir
[error]   -m, --sim <value>       Comma-separated list of short simulation names, or '*' for all. Available simulations: PostBytes, PostFile, PostLongBytes, PostLongString, PostString, SimpleGetMultiRoute, SimpleGet
[error]   -u, --users <value>     Number of concurrent users, default is 1
[error]   -d, --duration <value>  Single simulation duration in seconds, default is 10
[error]   -g, --gatling-reports   Generate Gatling reports for individuals sims, may significantly affect total time (disabled by default)
```

## Examples

1. Run all sims on all pekko-http servers with other options set to default:
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s pekko.* -m *
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
[info] ******* Test Suite report saved to /home/alice/projects/tapir/.sbt/matrix/perfTests/tapir-perf-tests-2024-01-22_16_33_14.csv
[info] ******* Test Suite report saved to /home/alice/projects/tapir/.sbt/matrix/perfTests/tapir-perf-tests-2024-01-22_16_33_14.html
```

These reports include information about throughput and latency of each server for each simulation.

How the aggregation works: After each non-warmup test the results are read from `simulation.log` produced by Gatling and aggregated by `GatlingLogProcessor`. 
The processor then uses 'com.codehale.metrics.Histogram' to calculate 
p99, p95, p75, and p50 percentiles for latencies of all requests sent during the simulation.

## Adding new servers and simulations

To add a new server, go to `src/main/scala` and put an object extending `sttp.tapir.perf.apis.ServerRunner` in a subpackage of `sttp.tapir.perf`. 
It should be automatically resoled by the `TypeScanner` utility used by the `PerfTestSuiteRunner`.

Similarly with simulations. Go to `src/test/scala` and a class extending `io.gatling.core.Predef.Simulation` under `sttp.tapir.perf`. See the `Simulations.scala` 
file for examples.
