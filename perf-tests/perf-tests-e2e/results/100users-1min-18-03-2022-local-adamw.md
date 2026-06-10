# Setup

2019 mbp, 2,3 GHz 8-Core Intel Core i9

# Results

* vanilla akka-http, multi-route: 23278 req/s
* tapir akka-http, multi-route: 8277 req/s

## After introducing `FilterServerEndpoints`

* vanilla akka-http, multi-route: 25531 req/s
* tapir akka-http, multi-route: 45407 req/s