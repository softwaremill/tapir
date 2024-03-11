# Performance tests

Performance tests are executed by running `PerfTestSuiteRunner`, which is a standard "Main" Scala application, configured by command line parameters. It executes a sequence of tests, where
each test consist of:

1. Starting a HTTP server if specified (Like Tapir-based Pekko, Vartx, http4s, or a "vanilla", tapirless one)
2. Running a simulation in warm-up mode (5 seconds, 3 concurrent users)
3. Running a simulation with user-defined duration and concurrent user count
4. Closing the server
5. Reading Gatling's simulation.log and building simulation results

The sequence is repeated for a set of servers multiplied by simulations. Afterwards, all individual simulation results will be aggregated into a single report. 
If no test servers are specified, only simulations are run, assuming a server started externally.
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
Generating Gatling reports is useful if you want to verify additional data like latency or throughput distribution over time.
If you want to run a test server separately from simulations, use a separate sbt session and start it using `ServerRunner`:

```
perfTests/runMain sttp.tapir.perf.apis.ServerRunner http4s.TapirMulti
```

This is useful when profiling, as `perfTests/runMain` will be a forked JVM isolated from the JVM that runs Gatling.

## Profiling 

To atach the profiler to a running server, it is recommended to use [async-profiler](https://github.com/async-profiler/async-profiler).
Start the profiler by calling:
```
asprof -e cpu,alloc,lock -f profile.jfr <PID>
```

After the simulations, you can open `recording.jfr` in IntelliJ IDEA or Java Mission Control to analyze performance metrics like heap and CPU usage.
It's also useful to build CPU flamegraphs with the [async-profiler converter](https://github.com/async-profiler/async-profiler?tab=readme-ov-file#download):
```
java -cp ./converter.jar jfr2flame ./profile.jfr flamegraph.html
```

After opening the flamegraph in your browser, use the spyglass icon to search for regular expressions and find the total % of registered samples matching the query. Searching for `tapir` will show you what's the Tapir's total share of the load. This can be a useful metric to compare before and after implementing performance improvements.

Note that profiling noticeably affects performance, so it's recommended to measure throughput/latency without the profiler attached.

## Examples

1. Run all sims on all pekko-http servers with other options set to default:
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s pekko.* -m *
```

2. Run all sims on http4s servers, with each simulation running for 5 seconds:
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s http4s.Tapir,http4s.TapirMulti,http4s.Vanilla,http4s.VanillaMulti -m * -d 5
```

3. Run some simulations on some servers, with 3 concurrent users instead of default 1, each simulation running for 15 seconds, 
and enabled Gatling report generation:
```
perfTests/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -s http4s.Tapir,netty.future.Tapir,play.Tapir -m PostLongBytes,PostFile -d 15 -u 3 -g
```

4. Run a netty-cats server with profiling, and then PostBytes and PostLongBytes simulation in a separate sbt session, for 25 seconds:
```
perfTests/runMain sttp.tapir.perf.apis.ServerRunner netty.cats.TapirMulti
// in a separate sbt session:
perfTest/Test/runMain sttp.tapir.perf.PerfTestSuiteRunner -m PostBytes,PostLongBytes -d 25
```

## Reports

Each single simulation results in a latency HDR Histogram report printed to stdout as well as a file:

```
[info] ******* Histogram saved to /home/kc/code/oss/tapir/.sbt/matrix/perfTests/SimpleGetSimulation-2024-02-26_10_30_22
```

You can use [HDR Histogram Plotter](https://hdrhistogram.github.io/HdrHistogram/plotFiles.html) to plot a set of such files.

The main report is generated after all tests, and contains results for standard Gatling latencies and mean throughput in a table combining
all servers and tests. They will be printed to a HTML and a CSV file after the suite finishes:
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

Similarly with simulations. Go to `src/test/scala` and a class extending `sttp.tapir.perf.PerfTestSuiteRunnerSimulation` under `sttp.tapir.perf`. See the `Simulations.scala` 
file for examples.

## Testing WebSockets

`WebSocketsSimulation` cannot be executed using `PerfTestSuiteRunner`, as it requires special warmup and injection setup, it also won't store gatling log in a format expected by our report builder.
For WebSockets we want to measure latency distribution, not throughput, so use given instructions to run it and read the report:

1. Adjust simulation parameters in the `sttp.tapir.perf.WebSocketsSimulation` class
2. Start a server using `ServerRunner`, for example:
```
perfTests/runMain sttp.tapir.perf.apis.ServerRunner http4s.Tapir
```
3. Run the simulation using Gatling's task:
```
perfTests/Gatling/testOnly sttp.tapir.perf.WebSocketsSimulation 
```
4. A HdrHistogram report will be printed to stdout and to a file. Check output for the full path.
5. Stop the server manually.
6. Use [HDR Histogram Plotter](https://hdrhistogram.github.io/HdrHistogram/plotFiles.html) to plot histogram file(s)
