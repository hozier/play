import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

interface FargateServiceConstructProps {
  cluster: ecs.Cluster;
  taskDefinition: ecs.FargateTaskDefinition;
  targetGroup: elbv2.ApplicationTargetGroup;
}

export class FargateServiceConstruct extends Construct {
  constructor(scope: Construct, id: string, props: FargateServiceConstructProps) {
    super(scope, id);

    const { cluster, taskDefinition, targetGroup } = props;

    const acceptedCounts = ["0", "1", "2", "3"]
    const requestedCount = process.env.DESIRED_COUNT!

    const fargateService = new ecs.FargateService(this, 'FargateService', {
      cluster,
      taskDefinition,
      assignPublicIp: true,
      desiredCount: parseInt(acceptedCounts.includes(requestedCount) ? requestedCount : "1"),
      vpcSubnets: { subnetType: cdk.aws_ec2.SubnetType.PUBLIC },
      circuitBreaker: { rollback: true },
    });

    fargateService.attachToApplicationTargetGroup(targetGroup);
  }
}
