# Running using the AWS serverless stack

Tapir server endpoints can be packaged and deployed as an [AWS Lambda](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html) function. To invoke the function, HTTP requests can be proxied through [AWS API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/welcome.html).

To configure API Gateway routes, and the Lambda function, tools like [AWS SAM](https://aws.amazon.com/serverless/sam/), [AWS CDK](https://aws.amazon.com/cdk/) or [Terraform](https://www.terraform.io/) can be used, to automate cloud deployments.

For an overview of how this works in more detail, see [this blog post](https://blog.softwaremill.com/tapir-serverless-a-proof-of-concept-6b8c9de4d396).

## Runtime

When deploying to Lambda, we need to chose one of the available runtimes provided by AWS.
Tapir can be deployed, either to custom runtime, or to Java one. There are corresponding classes for each of them:
  * The `AwsLambdaIORuntime` to implement the Lambda loop of reading the next request, computing and sending the response through [Lambda runtime API](https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html).
  * The `LambdaHandler` which utilizes [RequestStreamHandler](https://github.com/aws/aws-lambda-java-libs/blob/master/aws-lambda-java-core/src/main/java/com/amazonaws/services/lambda/runtime/RequestStreamHandler.java) interface for handling requests, response flow inside Java runtime.

## Serverless interpreters

Each of the aforementioned runtimes uses server interpreter, which takes tapir endpoints with associated server logic, and returns an `AwsRequest => F[AwsResponse]` function. Currently, only an interpreter integrating with cats-effect is available (`AwsCatsEffectServerInterpreter`).

To start using any of above runtimes with the interpreter, add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda" % "@VERSION@"
```

## Deployment

To configure API Gateway and the Lambda function, you can use:

* the `AwsSamInterpreter` which interprets tapir `Endpoints` into an AWS SAM template file
* or the `AwsTerraformInterpreter` which interprets `Endpoints` into terraform configuration file.

Add one of the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-sam" % "@VERSION@"
"com.softwaremill.sttp.tapir" %% "tapir-aws-terraform" % "@VERSION@"
"com.softwaremill.sttp.tapir" %% "tapir-aws-cdk" % "@VERSION@"
```

## Examples

In our [GitHub repository](https://github.com/softwaremill/tapir/tree/master/serverless/aws/examples/src/main/scala/sttp/tapir/serverless/aws/examples)
you'll find a `LambdaApiExample` handler which uses `AwsCatsEffectServerInterpreter` to route a hello endpoint along
with `SamTemplateExample`, `TerraformConfigExample` and `CdkAppExample` which interpret endpoints to SAM/Terraform/CDK application configuration. Go
ahead and clone tapir project and select `project awsExamples` from sbt shell.

Make sure you have [AWS command line tools installed](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html).

### SAM

To try it out using SAM template you don't need an AWS account.

* install [AWS SAM command line tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html)
* run `assembly` task and `runMain sttp.tapir.serverless.aws.examples.SamTemplateExample`
* open a terminal and in tapir root directory run `sam local start-api --warm-containers EAGER`

That will create `template.yaml` and start up AWS Api Gateway locally. Hello endpoint will be available
under `curl http://127.0.0.1:3000/api/hello`. First invocation will take a while but subsequent ones will be faster
since the created container will be reused.

### Terraform

To run the example using terraform you will need an AWS account, and an S3 bucket.

* install [Terraform](https://learn.hashicorp.com/tutorials/terraform/install-cli)
* run `assembly` task
* open a terminal in `tapir/serverless/aws/examples/target/jvm-2.13` directory. That's where the fat jar is saved. You
  need to upload it into your s3 bucket. Using command line
  tools: `aws s3 cp tapir-aws-examples.jar s3://{your-bucket}/{your-key}`.
* Run `runMain sttp.tapir.serverless.aws.examples.TerraformConfigExample {your-aws-region} {your-bucket} {your-key}`
* open terminal in tapir root directory, run `terraform init` and `terraform apply`

That will create `api_gateway.tf.json` configuration and deploy Api Gateway and lambda function to AWS. Terraform will
output the url of the created API Gateway which you can call followed by `/api/hello` path.

To destroy all the created resources run `terraform destroy`.

### CDK

To run the example using AWS CDK you will need an AWS account, or just Docker if you want to run it locally.

* First you need to install two things:
  * [AWS SAM command line tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html)
  * [NPM](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)
  * [AWS CDK Toolkit](https://docs.aws.amazon.com/cdk/v2/guide/cli.html)
* Then run assembly task and `runMain sttp.tapir.serverless.aws.examples.CdkAppExample` to generate CDK application template under `cdk` directory
* Go to `cdk` and run `npm install`
* To run locally, simply execute `cdk synth`, and `sam local start-api -t cdk.out/TapirCdkStack.template.json --warm-containers EAGER` 
* To deploy it to AWS you need to have configured aws cli, and then run `cdk deply`

## Scala.js interpreter

`LambdaApiJsExample` and `LambdaApiJsResourceExample` demonstrate how to create an API route,
that can be built into Node.js module with Scala.js plugin.
Such module can be deployed as an AWS Lambda function with Node.js runtime.
The main benefit is the reduced deployment time.
Initialization of JVM-based application (with `sam local`) took ~11 seconds on average, while Node.js based one only ~2 seconds.

`LambdaApiJsExample` uses `AwsFutureServerInterpreter` and `JsRoute[Future]`,
which is an alias for the route function `AwsJsRequest => Future[AwsJsResponse]`.

`LambdaApiJsResourceExample` builds a `cats.effect.Resource` and uses `AwsCatsEffectServerInterpreter` and `JsRoute[IO]`.

### SAM example

SAM template and application module can be generated and deployed locally with following commands:

* to generate AWS Lambda yaml file run `sbt "project awsExamples; runMain sttp.tapir.serverless.aws.examples.SamJsTemplateExample"`
* to build Node.js module run `sbt "project awsExamplesJS; fastLinkJS"`, it will create all-in-one JS file
  under `tapir/serverless/aws/examples/target/js-2.13/tapir-aws-examples-fastopt/main.js`
* open a terminal and in tapir root directory run `sam local start-api --warm-containers EAGER`
