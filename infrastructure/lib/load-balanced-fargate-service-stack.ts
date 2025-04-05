import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import { Construct } from 'constructs';
import { TaskDefinitionConstruct } from './constructs/task-definition-construct';
import { LoadBalancerConstruct } from './constructs/load-balancer-construct';
import { FargateServiceConstruct } from './constructs/fargate-service-construct';
import { HealthCheckConstruct } from './constructs/health-check-construct';

interface LoadBalancedFargateServiceStackProps extends cdk.StackProps {
  cluster: ecs.Cluster;
  repository: ecr.IRepository;
  imageTag: string;
}

export class LoadBalancedFargateServiceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: LoadBalancedFargateServiceStackProps) {
    super(scope, id, props);

    const { cluster, repository, imageTag } = props;

    const taskDefinition = new TaskDefinitionConstruct(this, 'TaskDefinitionConstruct', {
      repository,
      imageDigest: imageTag
    });

    const loadBalancer = new LoadBalancerConstruct(this, 'LoadBalancerConstruct', {
      vpc: cluster.vpc,
    });

    new FargateServiceConstruct(this, 'FargateServiceConstruct', {
      cluster,
      taskDefinition: taskDefinition.taskDefinition,
      targetGroup: loadBalancer.targetGroup,
    });

    // Custom Health Check
    new HealthCheckConstruct(this, 'HealthCheckConstruct', {
      targetGroup: loadBalancer.targetGroup,
    });
  }
}