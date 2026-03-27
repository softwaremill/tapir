# Running using the AWS serverless stack

Tapir server endpoints can be packaged and deployed as
an [AWS Lambda](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html) function.
This approach, known as the Fat Lambda function, utilizes a single lambda function for deploying multiple endpoints. To invoke the
function, HTTP requests can be proxied through [AWS API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/welcome.html). 

To configure API Gateway routes, and the Lambda function, tools like [AWS SAM](https://aws.amazon.com/serverless/sam/), [AWS CDK](https://aws.amazon.com/cdk/) or [Terraform](https://www.terraform.io/) can be used, to automate cloud deployments.

For an overview of how this works in more detail, see [this blog post](https://blog.softwaremill.com/tapir-serverless-a-proof-of-concept-6b8c9de4d396).

## Runtime & Server interpreters

Tapir supports three of the AWS Lambda runtimes: custom runtime, Java, and NodeJS. Below you have a list of classes that can be used as an entry point
to your Lambda application depending on runtime of your choice. Each one of them uses the server interpreter, whose responsibility is to transform Tapir
endpoints with associated server logic to a function like `AwsRequest => F[AwsResponse]` (or `AwsRequest => AwsResponse` for direct-style) in case of custom and Java runtime,
or `AwsJsRequest => Future[AwsJsResponse]` in case of NodeJS runtime. Currently, four server interpreters are available: `AwsCatsEffectServerInterpreter`
using cats-effect, `AwsZioServerInterpreter` using ZIO, `AwsFutureServerInterpreter` using Scala Future, and `AwsSyncServerInterpreter` using direct-style (no effect wrapper).
Custom runtime uses the cats-effect interpreter. Java runtime can use the cats-effect interpreter (`LambdaHandler`), the ZIO interpreter
(`ZioLambdaHandler`), or the direct-style interpreter (`SyncLambdaHandler`). NodeJS runtime can be used with both Future and cats-effect interpreters.
These are the corresponding classes for each of the supported runtimes:

* The `AwsLambdaIORuntime` for custom runtime. Implement the Lambda loop of reading the next request, computing and sending the response
  through [Lambda runtime API](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html).
* The `LambdaHandler` for Java runtime, which
  utilizes [RequestStreamHandler](https://github.com/aws/aws-lambda-java-libs/blob/master/aws-lambda-java-core/src/main/java/com/amazonaws/services/lambda/runtime/RequestStreamHandler.java)
  interface for handling requests, response flow inside Java runtime.
* The `ZioLambdaHandler` for Java runtime, using `AwsZioServerInterpreter` and ZIO effects.
* The `SyncLambdaHandler` for Java runtime, a direct-style alternative to `LambdaHandler` that uses `Identity` instead of cats-effect.
  Uses `AwsSyncServerInterpreter` and doesn't require any effect library.
* The `AwsJsRouteHandler` for NodeJS runtime. The main benefit is the reduced deployment time. Initialization of JVM-based application (
  with `sam local`) took ~11 seconds on average, while Node.js based one only ~2 seconds.

To start using any of the above add one of the following dependencies:

```scala
// for cats-effect (LambdaHandler, AwsLambdaIORuntime, AwsCatsEffectServerInterpreter)
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda" % "@VERSION@"
// for ZIO (ZioLambdaHandler, AwsZioServerInterpreter)
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda-zio" % "@VERSION@"
// for direct-style / Future (SyncLambdaHandler, AwsSyncServerInterpreter, AwsFutureServerInterpreter)
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda-core" % "@VERSION@"
```

## Usage examples

Example Lambda handler implementations:

* [Direct-style (SyncLambdaHandler)](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajvm/sttp/tapir/serverless/aws/examples/SyncLambdaApiExample.scala)
* [cats-effect (LambdaHandler)](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajvm/sttp/tapir/serverless/aws/examples/LambdaApiExample.scala) — see also the [V1 variant](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajvm/sttp/tapir/serverless/aws/examples/LambdaApiV1Example.scala)
* [ZIO (ZioLambdaHandler)](https://github.com/softwaremill/tapir/blob/master/serverless/aws/lambda-zio-tests/src/main/scala/sttp/tapir/serverless/aws/ziolambda/tests/ZioLambdaHandlerImpl.scala)
* [NodeJS with Future (AwsJsRouteHandler)](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajs/sttp/tapir/serverless/aws/examples/LambdaApiJsExample.scala) — see also the [cats-effect Resource variant](https://github.com/softwaremill/tapir/blob/master/serverless/aws/examples/src/main/scalajs/sttp/tapir/serverless/aws/examples/LambdaApiJsResourceExample.scala)

The type parameter on `LambdaHandler` and `SyncLambdaHandler` (`AwsRequest` or `AwsRequestV1`) determines the expected API Gateway request format. Use `AwsRequest` for API Gateway V2 (HTTP API) and `AwsRequestV1` for API Gateway V1 (REST API). V1 requests are automatically normalized to V2 internally.

## Deployment

To make it possible, to call your endpoints, you will need to deploy your application to Lambda, and configure Amazon API Gateway.
Tapir leverages ways of doing it provided by AWS, you can choose from: AWS SAM template file, terraform configuration, and AWS CDK.

You can start by adding one of the following dependencies to your project, and then follow examples:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-sam" % "@VERSION@"
"com.softwaremill.sttp.tapir" %% "tapir-aws-terraform" % "@VERSION@"
"com.softwaremill.sttp.tapir" %% "tapir-aws-cdk" % "@VERSION@"
```

### Examples

Go ahead and clone tapir project. To deploy you application to AWS you will need to have an AWS account
and [AWS command line tools installed](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html).

#### SAM

SAM can be deployed using Java runtime or NodeJS runtime. For each of these cases first you will have to install [AWS SAM command line tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html), and create a S3 bucket, that will be used during deployment. Before going further, open sbt shell, as it will be needed for both runtimes.

For Java runtime, use sbt to run `assembly` task, and then `runMain sttp.tapir.serverless.aws.examples.SamTemplateExample`, this will generate `template.yaml` sam file in main directory 

For NodeJS runtime, first generate AWS Lambda yaml file by execution inside sbt shell command `awsExamples/runMain sttp.tapir.serverless.aws.examples.SamJsTemplateExample`, and then build Node.js module with `awsExamplesJS/fastLinkJS`, it will create all-in-one JS file under `tapir/serverless/aws/examples/target/js-2.13/tapir-aws-examples-fastopt/main.js`

From now the steps for both runtimes are the same:
1. Before deploying, if you want to test your application locally, you will need Docker. Execute `sam local start-api --warm-containers EAGER`, there will be a link displayed at the console output
2. To deploy it to AWS, run `sam deploy --template-file template.yaml --stack-name sam-app --capabilities CAPABILITY_IAM --s3-bucket [name of your bucket]`. The console output should print url of the application, just add `/api/hello` to the end of it, and you should see `Hello!` message. Be aware in case of Java runtime, the first call can take a little longer as the application takes some time to start, but consecutive calls will be much faster.
3. When you want to rollback changes made on AWS, run `sam delete --stack-name sam-app`

#### Terraform

Terraform deployment requires you to have a S3 bucket.

1. Install [Terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli)
2. Run `assembly` task inside sbt shell
3. Open a terminal in `tapir/serverless/aws/examples/target/jvm-2.13` directory. That's where the fat jar is saved. You
   need to upload it into your s3 bucket. Using command line
   tools: `aws s3 cp tapir-aws-examples.jar s3://{your-bucket}/{your-key}`.
4. Run `runMain sttp.tapir.serverless.aws.examples.TerraformConfigExample {your-aws-region} {your-bucket} {your-key}` inside sbt shell
5. Open terminal in tapir root directory, run `terraform init` and `terraform apply`

That will create `api_gateway.tf.json` configuration and deploy Api Gateway and lambda function to AWS. Terraform will
output the url of the created API Gateway which you can call followed by `/api/hello` path.

To destroy all the created resources run `terraform destroy`.

#### CDK

1. First you need to install:
    * [NPM](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)
    * [AWS CDK Toolkit](https://docs.aws.amazon.com/cdk/v2/guide/cli.html)
2. Open sbt shell, then run `assembly` task, and execute `runMain sttp.tapir.serverless.aws.examples.CdkAppExample` to generate CDK application template under `cdk`
   directory
3. Go to `cdk` and run `npm install`, it will create all files needed for the deployment
4. Before deploying, if you want to test your application locally, you will need Docker
   and [AWS SAM command line tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html)
   , then execute `cdk synth`, and `sam local start-api -t cdk.out/TapirCdkStack.template.json --warm-containers EAGER`
5. To deploy it to AWS simply run `cdk deploy`
6. When you want to rollback changes made on AWS, run `cdk destroy`
