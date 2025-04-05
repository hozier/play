import * as cr from 'aws-cdk-lib/custom-resources';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as cdk from 'aws-cdk-lib';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export class ForceDeploymentConstruct extends Construct {
  constructor(scope: Construct, id: string, fargateService: ecs.FargateService, cluster: ecs.ICluster) {
    super(scope, id);

    new cr.AwsCustomResource(this, 'ForceDeployment', {
      onCreate: {
        service: 'ECS',
        action: 'updateService',
        parameters: {
          cluster: cluster.clusterName,
          service: fargateService.serviceName,
          forceNewDeployment: true,
        },
        physicalResourceId: cr.PhysicalResourceId.of('ForceDeployment'),
      },
      policy: cr.AwsCustomResourcePolicy.fromStatements([
        new iam.PolicyStatement({
          actions: ['ecs:UpdateService'],
          resources: [
            `arn:aws:ecs:${cdk.Stack.of(this).region}:${cdk.Stack.of(this).account}:service/${cluster.clusterName}/${fargateService.serviceName}`,
          ],
        }),
      ]),
    });
  }
}
