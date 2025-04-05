#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { SERVICE_NAME, env } from '../config/environments';
import { AssemblerStack } from '../lib/assembler-stack';

const app = new cdk.App();

AssemblerStack.init(app, 'init-infra', { 
  env, 
  stackName: `app-${SERVICE_NAME}-assembler-stack`,
  registry: process.env.REGISTRY!,
  repository: process.env.REPOSITORY!,
  imageTag: process.env.IMAGE_TAG!
});