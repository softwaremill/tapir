import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigw from 'aws-cdk-lib/aws-apigateway';

export class TapirCdkStack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const lambdaJar = new lambda.Function(this, '{{lambdaName}}', {
      runtime: {{runtime}},
      code: lambda.Code.fromAsset('{{jarPath}}'),
      handler: '{{handler}}',
      timeout: cdk.Duration.seconds({{timeout}}),
      memorySize: {{memorySize}}
    });

    const api = new apigw.LambdaRestApi(this, '{{apiName}}', {
      handler: lambdaJar,
      proxy: false
    });

{{stacks}}
  }
}
