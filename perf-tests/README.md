# Setup

To test vanilla akka-http load, run these two commanda in two seperate `sbt` instances:
```
perfServerAkkaHttpOnly/run
perfTests / Gatling / testOnly perfTests.AkkaHttpOnlySimulation
```

To test tapir with akka-http load, run these two commanda in two seperate `sbt` instances:
```
perfServerAkkaHttpTapir/run
perfTests / Gatling / testOnly perfTests.AkkaHttpTapirSimulation
```


# Test Results for 1000 users during 5 minutes

akka-http only:
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    4837004 (OK=4837004 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                   3876 (OK=3876   KO=-     )
> mean response time                                    62 (OK=62     KO=-     )
> std deviation                                         48 (OK=48     KO=-     )
> response time 50th percentile                         59 (OK=59     KO=-     )
> response time 75th percentile                         76 (OK=76     KO=-     )
> response time 95th percentile                        119 (OK=119    KO=-     )
> response time 99th percentile                        168 (OK=168    KO=-     )
> mean requests/sec                                16069.781 (OK=16069.781 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       4836119 (100%)
> 800 ms < t < 1200 ms                                  25 (  0%)
> t > 1200 ms                                          860 (  0%)
> failed                                                 0 (  0%)
================================================================================
Reports generated in 0s.
Please open the following file: /home/felix/code/work/tapir/perf-tests/simulations/target/jvm-2.13/gatling/akkahttponlysimulation-20220222110602734/index.html
[info] Simulation AkkaHttpOnlySimulation successful.
[info] Simulation(s) execution ended.
[success] Total time: 326 s (05:26), completed Feb 22, 2022, 11:11:27 AM
```


akka-http with tapir:
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    4107241 (OK=4107241 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                   7597 (OK=7597   KO=-     )
> mean response time                                    73 (OK=73     KO=-     )
> std deviation                                         75 (OK=75     KO=-     )
> response time 50th percentile                         59 (OK=59     KO=-     )
> response time 75th percentile                        103 (OK=103    KO=-     )
> response time 95th percentile                        198 (OK=198    KO=-     )
> response time 99th percentile                        288 (OK=288    KO=-     )
> mean requests/sec                                13645.319 (OK=13645.319 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       4106667 (100%)
> 800 ms < t < 1200 ms                                  42 (  0%)
> t > 1200 ms                                          532 (  0%)
> failed                                                 0 (  0%)
================================================================================
Reports generated in 0s.
Please open the following file: /home/felix/code/work/tapir/perf-tests/simulations/target/jvm-2.13/gatling/akkahttptapirsimulation-20220222105743299/index.html
[info] Simulation AkkaHttpTapirSimulation successful.
[info] Simulation(s) execution ended.
[success] Total time: 322 s (05:22), completed Feb 22, 2022, 11:03:04 AM
```
