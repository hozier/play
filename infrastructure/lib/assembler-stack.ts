import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import { IamRoleStack } from './iam-role';
import { LoadBalancedFargateServiceStack } from './load-balanced-fargate-service-stack'; // Ensure this is the correct path
import { SERVICE_NAME } from '../config/environments';

export interface AssemblerStackProps extends cdk.StackProps {
  registry: string;
  repository: string;
  imageTag: string;
}

export class AssemblerStack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props: AssemblerStackProps) {
    super(scope, id, props);

    const { repository, imageTag } = props;

    const vpc = new ec2.Vpc(this, 'Vpc', { maxAzs: 2 });
    const cluster = new ecs.Cluster(this, 'Cluster', { vpc });

    const iamRoleStack = new IamRoleStack(scope, 'iam-builder', { stackName: `app-${SERVICE_NAME}-iam-role-stack`, env: props?.env });

    const loadBalancedFargateServiceStack = new LoadBalancedFargateServiceStack(this, 'assemble-elbv2',{
      stackName: `app-${SERVICE_NAME}-load-balanced-fargate-service-stack`,
      env: props?.env,
      cluster,
      imageTag,
      repository: ecr.Repository.fromRepositoryName(this, 'EcrRepository', repository),
    });

    // Add dependency to ensure ECS stack deploys after IAM roles
    loadBalancedFargateServiceStack.addDependency(iamRoleStack);
  }

  public static init(scope: cdk.App, id: string, props: AssemblerStackProps) {
    return new AssemblerStack(scope, id, props);
  }
}
