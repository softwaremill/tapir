# Running using the AWS serverless stack

Tapir server endpoints can be packaged and deployed as an [AWS
Lambda](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html)
function. You can utilize a single lambda function for multiple endpoints ("Fat
Lambda"), or deploy the same jar multiple times, so that each handles its own
endpoint or subset of endpoints. To invoke the function, HTTP requests can be
proxied through [AWS API
Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/welcome.html). 

To configure API Gateway routes, and the Lambda function, tools like [AWS
SAM](https://aws.amazon.com/serverless/sam/), [AWS
CDK](https://aws.amazon.com/cdk/) or [Terraform](https://www.terraform.io/) can
be used, to automate cloud deployments.

For an overview of how this works in more detail, see [this blog
post](https://blog.softwaremill.com/tapir-serverless-a-proof-of-concept-6b8c9de4d396).

## Runtimes & interpreters

AWS Lambda supports several
[runtimes](https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html) —
the language-specific environment in which your function code executes. Tapir
supports three of them, each described in its own section below.

Note: the handler type parameter (`AwsRequest` or `AwsRequestV1`) determines the
expected API Gateway request format. Use `AwsRequest` for API Gateway V2 (HTTP
API) and `AwsRequestV1` for API Gateway V1 (REST API). V1 requests are
automatically normalized to V2 internally.

### Java runtime

With the Java runtime, you package your code as a fat JAR (via `assembly`) and
upload it to AWS, which manages the JVM. The Lambda entry point implements the
AWS
[RequestStreamHandler](https://github.com/aws/aws-lambda-java-libs/blob/master/aws-lambda-java-core/src/main/java/com/amazonaws/services/lambda/runtime/RequestStreamHandler.java)
interface, which is provided by the `aws-lambda-java-runtime-interface-client`
dependency (pulled in transitively by all tapir AWS Lambda modules). You can use
this with direct-style, cats-effect, or ZIO:

#### Direct-style

No effect library needed. Extend `SyncLambdaHandler`, provide your endpoints via
`getAllEndpoints`, and you're done — the class directly implements
`RequestStreamHandler`.

Uses `AwsSyncServerInterpreter` (`AwsRequest => AwsResponse`).

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda-core" % "1.13.16"
```

Example:
[SyncLambdaApiExample](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajvm/sttp/tapir/serverless/aws/examples/SyncLambdaApiExample.scala)

#### cats-effect

Extend `LambdaHandler[F, R]`, provide your endpoints via `getAllEndpoints`, and
implement `handleRequest` by calling `process(input, output).unsafeRunSync()`.

Uses `AwsCatsEffectServerInterpreter` (`AwsRequest => F[AwsResponse]`).

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda" % "1.13.16"
```

Examples:
[LambdaApiExample](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajvm/sttp/tapir/serverless/aws/examples/LambdaApiExample.scala),
[V1
variant](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajvm/sttp/tapir/serverless/aws/examples/LambdaApiV1Example.scala)

#### ZIO

Create a handler instance via `ZioLambdaHandler.default(endpoints)`, then call
`handler.process[AwsRequest](input, output)` from a `RequestStreamHandler`
implementation, running the ZIO effect with `Runtime.default.unsafe.run(...)`.

Uses `AwsZioServerInterpreter` (`AwsRequest => RIO[Env, AwsResponse]`).

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda-zio" % "1.13.16"
```

Example:
[ZioLambdaHandlerImpl](https://github.com/softwaremill/tapir/blob/master/serverless/aws/lambda-zio-tests/src/main/scala/sttp/tapir/serverless/aws/ziolambda/tests/ZioLambdaHandlerImpl.scala)

### Custom runtime

As an alternative to the AWS-provided Java runtime, you can use a [custom
runtime](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html) where
your application includes its own runtime loop that polls the Lambda Runtime API
for invocations via HTTP. This can be useful when running in custom containers
or with GraalVM native images. The tradeoff is an additional dependency on an
HTTP client (sttp client4 with the fs2 backend).

#### cats-effect

Extend `AwsLambdaIORuntime` and provide your `endpoints`. The base class
implements `main` and runs the polling loop via `AwsLambdaRuntime`.

Uses `AwsCatsEffectServerInterpreter` (`AwsRequest => F[AwsResponse]`).

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda" % "1.13.16"
```

### NodeJS runtime

You can also compile your Scala code to JavaScript via Scala.js and run it on
Node.js. The main benefit are faster cold starts, though with AWS's SnapStart,
this might or might not be a significant advantage.

Handler functions are exported to JavaScript using `@JSExportTopLevel` and
return a `js.Promise[AwsJsResponse]`. Use `AwsJsRouteHandler` to bridge between
the JS request/response types and tapir's `Route[F]`. You can use this with
either Future or cats-effect:

#### Future

Uses `AwsFutureServerInterpreter` (`AwsRequest => Future[AwsResponse]`).

```scala
"com.softwaremill.sttp.tapir" %%% "tapir-aws-lambda-core" % "1.13.16"
```

Example:
[LambdaApiJsExample](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajs/sttp/tapir/serverless/aws/examples/LambdaApiJsExample.scala)

#### cats-effect

Uses `AwsCatsEffectServerInterpreter` (`AwsRequest => IO[AwsResponse]`).
Supports both plain `Route[IO]` (via `catsIOHandler`) and `Resource[IO,
Route[IO]]` (via `catsResourceHandler`).

```scala
"com.softwaremill.sttp.tapir" %%% "tapir-aws-lambda" % "1.13.16"
```

Example:
[LambdaApiJsResourceExample](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajs/sttp/tapir/serverless/aws/examples/LambdaApiJsResourceExample.scala)

## Deployment

To make it possible, to call your endpoints, you will need to deploy your
application to Lambda, and configure Amazon API Gateway. Tapir leverages ways of
doing it provided by AWS, you can choose from: AWS SAM template file, terraform
configuration, and AWS CDK.

You can start by adding one of the following dependencies to your project, and
then follow examples:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-sam" % "1.13.16"
"com.softwaremill.sttp.tapir" %% "tapir-aws-terraform" % "1.13.16"
"com.softwaremill.sttp.tapir" %% "tapir-aws-cdk" % "1.13.16"
```

### Examples

Go ahead and clone tapir project. To deploy you application to AWS you will need
to have an AWS account and [AWS command line tools
installed](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html).

#### SAM

SAM can be deployed using Java runtime or NodeJS runtime. For each of these
cases first you will have to install [AWS SAM command line
tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html),
and create a S3 bucket, that will be used during deployment. Before going
further, open sbt shell, as it will be needed for both runtimes.

For Java runtime, use sbt to run `assembly` task, and then `runMain
sttp.tapir.serverless.aws.examples.SamTemplateExample`, this will generate
`template.yaml` sam file in main directory 

For NodeJS runtime, first generate AWS Lambda yaml file by execution inside sbt
shell command `awsExamples/runMain
sttp.tapir.serverless.aws.examples.SamJsTemplateExample`, and then build Node.js
module with `awsExamplesJS/fastLinkJS`, it will create all-in-one JS file under
`tapir/serverless/aws/examples/target/js-2.13/tapir-aws-examples-fastopt/main.js`

From now the steps for both runtimes are the same:
1. Before deploying, if you want to test your application locally, you will need
   Docker. Execute `sam local start-api --warm-containers EAGER`, there will be
   a link displayed at the console output
2. To deploy it to AWS, run `sam deploy --template-file template.yaml
   --stack-name sam-app --capabilities CAPABILITY_IAM --s3-bucket [name of your
   bucket]`. The console output should print url of the application, just add
   `/api/hello` to the end of it, and you should see `Hello!` message. Be aware
   in case of Java runtime, the first call can take a little longer as the
   application takes some time to start, but consecutive calls will be much
   faster.
3. When you want to rollback changes made on AWS, run `sam delete --stack-name
   sam-app`

#### Terraform

Terraform deployment requires you to have a S3 bucket.

1. Install
   [Terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli)
2. Run `assembly` task inside sbt shell
3. Open a terminal in `tapir/serverless/aws/examples/target/jvm-2.13` directory.
   That's where the fat jar is saved. You need to upload it into your s3 bucket.
   Using command line tools: `aws s3 cp tapir-aws-examples.jar
   s3://{your-bucket}/{your-key}`.
4. Run `runMain sttp.tapir.serverless.aws.examples.TerraformConfigExample
   {your-aws-region} {your-bucket} {your-key}` inside sbt shell
5. Open terminal in tapir root directory, run `terraform init` and `terraform
   apply`

That will create `api_gateway.tf.json` configuration and deploy Api Gateway and
lambda function to AWS. Terraform will output the url of the created API Gateway
which you can call followed by `/api/hello` path.

To destroy all the created resources run `terraform destroy`.

#### CDK

1. First you need to install:
    * [NPM](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)
    * [AWS CDK Toolkit](https://docs.aws.amazon.com/cdk/v2/guide/cli.html)
2. Open sbt shell, then run `assembly` task, and execute `runMain
   sttp.tapir.serverless.aws.examples.CdkAppExample` to generate CDK application
   template under `cdk` directory
3. Go to `cdk` and run `npm install`, it will create all files needed for the
   deployment
4. Before deploying, if you want to test your application locally, you will need
   Docker and [AWS SAM command line
   tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html)
   , then execute `cdk synth`, and `sam local start-api -t
   cdk.out/TapirCdkStack.template.json --warm-containers EAGER`
5. To deploy it to AWS simply run `cdk deploy`
6. When you want to rollback changes made on AWS, run `cdk destroy`
