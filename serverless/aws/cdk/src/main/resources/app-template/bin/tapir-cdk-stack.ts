#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { TapirCdkStack } from '../lib/tapir-cdk-stack';

const app = new cdk.App();
new TapirCdkStack(app, 'TapirCdkStack');
