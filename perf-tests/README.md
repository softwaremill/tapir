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

# Test results for 1000 users during 5 minutes (seperate method)

akka-http only:

```
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
```

akka-http with tapir:

```
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
```
