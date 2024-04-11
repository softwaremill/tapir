### Developer notes

#### Testing
```
sbt
project openapiCodegen2_12
test
scripted
```

#### Local debugging
```
cd sbt/sbt-openapi-codegen/src/sbt-test/sbt-openapi-codegen/minimal/
sbt -Dplugin.version=0.1-SNAPSHOT run
cat target/swagger.yaml
cat target/scala-2.13/classes/sttp/tapir/generated/TapirGeneratedEndpoints.scala
```
