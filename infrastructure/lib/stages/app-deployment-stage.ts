import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import { LoadBalancedFargateServiceStack } from '../stacks/load-balanced-fargate-service-stack';
import { SERVICE_NAME } from '../../config/environments';
import { PrerequisitesStack } from '../stacks/prerequisites-stack';

export interface AssemblerStackProps extends cdk.StackProps {
  registry: string;
  repository: string;
  imageTag: string;
}

export class AppDeploymentStage extends cdk.Stage {
  constructor(scope: cdk.App, id: string, props: AssemblerStackProps) {
    super(scope, id, props);

    const { imageTag } = props;

    const prerequisitesStack = new PrerequisitesStack(scope, 'PrereqsStack', {
      stackName: `app-${SERVICE_NAME}-prerequisites-stack`,
      actionRoleArn: `arn:aws:iam::${props.env?.account}:role/some-role`,
      env: props?.env,
    });

    // Use the VPC, IAM role, and ECR repository from PrerequisitesStack
    const cluster = new ecs.Cluster(this, 'Cluster', { vpc: prerequisitesStack.vpc });

    const loadBalancedFargateServiceStack = new LoadBalancedFargateServiceStack(this, 'assemble-elbv2', {
      stackName: `app-${SERVICE_NAME}-load-balanced-fargate-service-stack`,
      env: props?.env,
      cluster,
      imageTag,
      repository: prerequisitesStack.ecrRepo,
    });

    // Add dependency to ensure ECS stack deploys after PrereqsStack
    loadBalancedFargateServiceStack.addDependency(prerequisitesStack);
  }

  public static init(scope: cdk.App, id: string, props: AssemblerStackProps) {
    return new AppDeploymentStage(scope, id, props);
  }
}
