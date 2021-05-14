# Running behind AWS API Gateway

[AWS API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/welcome.html) provides a proxy integration
with [AWS Lambda](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-lambda.html) which allows
you to implement API routes using Lambda functions. [AWS SAM](https://aws.amazon.com/serverless/sam/) on the other hand provides a configuration mechanism
for binding HTTP APIs to Lambda functions.

This concept of serverless API has been adapted to Tapir in form of two components.

The first one is `AwsServerInterpreter` which routes AWS API Gateway requests to responses just as any other server interpreter does.
It should be used in your lambda function code.
```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-lambda" % "@VERSION@"
```

The second one is `AwsSamInterpreter` which interprets Tapir `Endpoints` into AWS SAM configuration file.
It should be used to configure your API Gateway.
```scala
"com.softwaremill.sttp.tapir" %% "tapir-aws-sam" % "@VERSION@"
```

In our [GitHub repository](https://github.com/softwaremill/tapir/tree/master/serverless/aws/examples/src/main/scala/sttp/tapir/serverless/aws/examples/LambdaApiExample) 
you'll find a runnable example which uses [AWS SAM command line tool](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-command-reference.html)
to run a "hello world" serverless application locally. 
