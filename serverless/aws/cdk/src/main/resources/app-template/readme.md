# AWS CDK Stack

## Prerequisites 

- Install [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
- Install [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)
- Install [AWS CDK Toolkit](https://docs.aws.amazon.com/cdk/v2/guide/cli.html)
- Configure your account ```aws configure```


## How to run service locally

```
npm install
cdk synth
sam local start-api -t cdk.out/TapirCdkStack.template.json
```