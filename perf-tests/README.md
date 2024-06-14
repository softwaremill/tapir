# Performance tests

To work with performance tests, make sure you are running JDK 21+, and that the `ALSO_LOOM` environment variable is set, because the `perf-tests` project includes `tapir-nima`, which require Loom JDK feature to be available.

Performance tests are executed by running `perfTests/Gatling/testOnly sttp.tapir.perf.SimulationClassName`, assuming that a server under tests is available on `localhost:8080`.

## Starting the server
To run a test server, use a separate sbt session and start it using `ServerRunner`:
```
perfTests/runMain sttp.tapir.perf.apis.ServerRunner http4s.TapirMulti
```
Run it without server name to see a list of all available names. 
Exception: If you're testing `NettySyncServer` (tapir-server-netty-sync), its server runner is located elsewhere:
```
nettyServerSync3/Test/runMain sttp.tapir.netty.sync.perf.NettySyncServerRunner
```
This is caused by `perf-tests` using Scala 2.13 forced by Gatling, while `NettySyncServer` is written excluisively for Scala 3.

## Configuring and running simulations

Simulations can be found in `sttp.tapir.perf.Simulations.scala`. To run one, use Gatling/testOnly:

```
perfTests/Gatling/testOnly sttp.tapir.perf.SimpleGetSimulation
```

The simulation will first run in warmup mode, then it will run with specified user count and duration. To set these values, use system properties:
`tapir.perf.user-count` and `tapir.perf.duration-seconds`. These values can be passed to sbt console on startup:
```
sbt -Dtapir.perf.user-count=100 -Dtapir.perf.duration-seconds=60
```
or within an already running interactive sbt session:
```
set ThisBuild/javaOptions += "-Dtapir.perf.user-count=100"
```
If not set, default values will be used (see `sttp.tapir.perf.CommonSimulations`).

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

## Latency reports

Additionally to standard Gatling reports, each single simulation results in a latency HDR Histogram report printed to stdout as well as a file:

```
[info] ******* Histogram saved to /home/kc/code/oss/tapir/.sbt/matrix/perfTests/SimpleGetSimulation-2024-02-26_10_30_22
```

You can use [HDR Histogram Plotter](https://hdrhistogram.github.io/HdrHistogram/plotFiles.html) to plot a set of such files.

## Adding new servers and simulations

To add a new server, go to `src/main/scala` and put an object extending `sttp.tapir.perf.apis.ServerRunner` in a subpackage of `sttp.tapir.perf`. 
It should be automatically resolved by the `TypeScanner` utility used by the `ServerRunner`.

Similarly with simulations. Go to `src/test/scala` and a class extending `sttp.tapir.perf.PerfTestSuiteRunnerSimulation` under `sttp.tapir.perf`. See the `Simulations.scala` 
file for examples.

