#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { SERVICE_NAME, env } from '../config/environments';
import { AppDeploymentStage } from '../lib/stages/app-deployment-stage';

const app = new cdk.App();

AppDeploymentStage.init(app, 'app-deployment-stage', {
  env,
  stackName: `app-${SERVICE_NAME}-deployment-stage`,
  registry: process.env.REGISTRY!,
  repository: process.env.REPOSITORY!,
  imageTag: process.env.IMAGE_TAG!
});