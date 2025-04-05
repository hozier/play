import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

interface HealthCheckConstructProps {
  targetGroup: elbv2.ApplicationTargetGroup;
}

export class HealthCheckConstruct extends Construct {
  constructor(scope: Construct, id: string, props: HealthCheckConstructProps) {
    super(scope, id);

    const { targetGroup } = props;

    const healthCheckFunction = new lambda.Function(this, 'HealthCheckFunction', {
      runtime: lambda.Runtime.NODEJS_20_X,
      handler: 'index.handler',
      code: lambda.Code.fromInline(`
        const AWS = require('aws-sdk');
        const elbv2 = new AWS.ELBv2();

        exports.handler = async (event) => {
          const targetGroupArn = event.ResourceProperties.TargetGroupArn;

          try {
            const health = await elbv2.describeTargetHealth({ TargetGroupArn: targetGroupArn }).promise();
            const unhealthyTargets = health.TargetHealthDescriptions.filter(
              target => target.TargetHealth.State !== 'healthy'
            );

            if (unhealthyTargets.length > 0) {
              throw new Error('Unhealthy targets detected.');
            }

            return { PhysicalResourceId: targetGroupArn };
          } catch (error) {
            throw error;
          }
        };
      `),
    });

    healthCheckFunction.addToRolePolicy(new iam.PolicyStatement({
      actions: ['elasticloadbalancing:DescribeTargetHealth'],
      resources: [targetGroup.targetGroupArn],
    }));
  }
}
