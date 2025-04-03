#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { ServiceStack } from '../lib/service-stack';
import { IamRoleStack } from '../lib/iam-role';
import { EcsServiceStack } from '../lib/ecs-service-stack';
import { SERVICE_NAME } from '../config/environments';

const app = new cdk.App();

// Instantiate the IamRoleStack
const iamRoleStack = new IamRoleStack(app, `app-${SERVICE_NAME}-iam-role-stack`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
});

// Instantiate the EcsTaskDefinitionStack
const ecsServiceStack = new EcsServiceStack(app, `app-${SERVICE_NAME}-ecs-service-stack`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
});

// Add dependency to ensure ECS stack deploys after IAM roles
ecsServiceStack.addDependency(iamRoleStack);

// Instantiate the ServiceStack
const serviceStack = new ServiceStack(app, `app-${SERVICE_NAME}-service-stack`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
});

// Add dependencies to ensure proper stack order
serviceStack.addDependency(iamRoleStack);
serviceStack.addDependency(ecsServiceStack);