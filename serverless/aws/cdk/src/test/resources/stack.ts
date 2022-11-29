import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigw from 'aws-cdk-lib/aws-apigateway';

export class TapirCdkStack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const lambdaJar = new lambda.Function(this, 'TapirHandler', {
      runtime: lambda.Runtime.JAVA_11,
      code: lambda.Code.fromAsset('..serverless/aws/cdk/target/jvm-2.13/tapir-aws-cdk.jar'),
      handler: 'sttp.tapir.serverless.aws.cdk.test.IOLambdaHandlerV1::handleRequest',
      timeout: cdk.Duration.seconds(20),
      memorySize: 2048
    });

    const api = new apigw.LambdaRestApi(this, 'API', {
      handler: lambdaJar,
      proxy: false
    });

    // GET /hello
    const hello = api.root.addResource('hello');
    hello.addMethod('GET');

    // GET /hello/{id}
    const helloIdParam = hello.addResource('{id}');
    helloIdParam.addMethod('GET');
  }
}
