# Tapir Performance Test
Number of users: 100
Time per test: 5min
Executed on: 09-03-2022

## Setup used
Running on a single machine (Thinkpad P17), in a single sbt intance, through single command tasks in the `perfTests` subproject
Network communication through localhost:8080

## Summary
```
    Command Name                             : requests sent
[1] perfTests/akkaHttpTapirMulti             :    2407641 
[2] perfTests/akkaHttpTapir                  :    4672354 
[3] perfTests/akkaHttpVanillaMulti           :    4476438 
[4] perfTests/akkaHttpVanilla                :    5249844 
[5] perfTests/http4sTapirMulti               :    2132484 
[6] perfTests/http4sTapir                    :    2667908 
[7] perfTests/http4sVanillaMulti             :    3334804 
[8] perfTests/http4sVanilla                  :    3365475 

```
## Full results
### [1] perfTests/akkaHttpTapirMulti
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    2407641 (OK=2407641 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                   2377 (OK=2377   KO=-     )
> mean response time                                    12 (OK=12     KO=-     )
> std deviation                                         12 (OK=12     KO=-     )
> response time 50th percentile                         10 (OK=10     KO=-     )
> response time 75th percentile                         16 (OK=16     KO=-     )
> response time 95th percentile                         29 (OK=29     KO=-     )
> response time 99th percentile                         43 (OK=43     KO=-     )
> mean requests/sec                                7998.807 (OK=7998.807 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       2407575 (100%)
> 800 ms < t < 1200 ms                                  20 (  0%)
> t > 1200 ms                                           46 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [2] perfTests/akkaHttpTapir
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    4672354 (OK=4672354 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                   1464 (OK=1464   KO=-     )
> mean response time                                     6 (OK=6      KO=-     )
> std deviation                                          6 (OK=6      KO=-     )
> response time 50th percentile                          6 (OK=6      KO=-     )
> response time 75th percentile                          8 (OK=8      KO=-     )
> response time 95th percentile                         12 (OK=12     KO=-     )
> response time 99th percentile                         18 (OK=18     KO=-     )
> mean requests/sec                                15522.771 (OK=15522.771 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       4672310 (100%)
> 800 ms < t < 1200 ms                                  17 (  0%)
> t > 1200 ms                                           27 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [3] perfTests/akkaHttpVanillaMulti
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    4476438 (OK=4476438 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                    981 (OK=981    KO=-     )
> mean response time                                     6 (OK=6      KO=-     )
> std deviation                                          5 (OK=5      KO=-     )
> response time 50th percentile                          6 (OK=6      KO=-     )
> response time 75th percentile                          8 (OK=8      KO=-     )
> response time 95th percentile                         13 (OK=13     KO=-     )
> response time 99th percentile                         18 (OK=18     KO=-     )
> mean requests/sec                                14871.887 (OK=14871.887 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       4476416 (100%)
> 800 ms < t < 1200 ms                                  22 (  0%)
> t > 1200 ms                                            0 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [4] perfTests/akkaHttpVanilla
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    5249844 (OK=5249844 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                    612 (OK=612    KO=-     )
> mean response time                                     5 (OK=5      KO=-     )
> std deviation                                          4 (OK=4      KO=-     )
> response time 50th percentile                          5 (OK=5      KO=-     )
> response time 75th percentile                          7 (OK=7      KO=-     )
> response time 95th percentile                         10 (OK=10     KO=-     )
> response time 99th percentile                         15 (OK=15     KO=-     )
> mean requests/sec                                17441.342 (OK=17441.342 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       5249844 (100%)
> 800 ms < t < 1200 ms                                   0 (  0%)
> t > 1200 ms                                            0 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [5] perfTests/http4sTapirMulti
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    2132484 (OK=2132484 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                   1433 (OK=1433   KO=-     )
> mean response time                                    14 (OK=14     KO=-     )
> std deviation                                         14 (OK=14     KO=-     )
> response time 50th percentile                         11 (OK=11     KO=-     )
> response time 75th percentile                         19 (OK=19     KO=-     )
> response time 95th percentile                         38 (OK=38     KO=-     )
> response time 99th percentile                         57 (OK=57     KO=-     )
> mean requests/sec                                7084.664 (OK=7084.664 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       2132390 (100%)
> 800 ms < t < 1200 ms                                  86 (  0%)
> t > 1200 ms                                            8 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [6] perfTests/http4sTapir
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    2667908 (OK=2667908 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                    954 (OK=954    KO=-     )
> mean response time                                    11 (OK=11     KO=-     )
> std deviation                                         10 (OK=10     KO=-     )
> response time 50th percentile                          9 (OK=9      KO=-     )
> response time 75th percentile                         15 (OK=15     KO=-     )
> response time 95th percentile                         28 (OK=28     KO=-     )
> response time 99th percentile                         43 (OK=43     KO=-     )
> mean requests/sec                                8863.482 (OK=8863.482 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       2667875 (100%)
> 800 ms < t < 1200 ms                                  33 (  0%)
> t > 1200 ms                                            0 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [7] perfTests/http4sVanillaMulti
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    3334804 (OK=3334804 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                    678 (OK=678    KO=-     )
> mean response time                                     9 (OK=9      KO=-     )
> std deviation                                          8 (OK=8      KO=-     )
> response time 50th percentile                          7 (OK=7      KO=-     )
> response time 75th percentile                         12 (OK=12     KO=-     )
> response time 95th percentile                         22 (OK=22     KO=-     )
> response time 99th percentile                         34 (OK=34     KO=-     )
> mean requests/sec                                11079.083 (OK=11079.083 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       3334804 (100%)
> 800 ms < t < 1200 ms                                   0 (  0%)
> t > 1200 ms                                            0 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
### [8] perfTests/http4sVanilla
```
================================================================================
---- Global Information --------------------------------------------------------
> request count                                    3365475 (OK=3365475 KO=0     )
> min response time                                      0 (OK=0      KO=-     )
> max response time                                    616 (OK=616    KO=-     )
> mean response time                                     9 (OK=9      KO=-     )
> std deviation                                          8 (OK=8      KO=-     )
> response time 50th percentile                          7 (OK=7      KO=-     )
> response time 75th percentile                         12 (OK=12     KO=-     )
> response time 95th percentile                         22 (OK=22     KO=-     )
> response time 99th percentile                         35 (OK=35     KO=-     )
> mean requests/sec                                11180.98 (OK=11180.98 KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                       3365475 (100%)
> 800 ms < t < 1200 ms                                   0 (  0%)
> t > 1200 ms                                            0 (  0%)
> failed                                                 0 (  0%)
================================================================================
```
