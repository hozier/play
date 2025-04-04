import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

interface LoadBalancerConstructProps {
  vpc: ec2.IVpc;
}

export class LoadBalancerConstruct extends Construct {
  public readonly loadBalancer: elbv2.ApplicationLoadBalancer;
  public readonly targetGroup: elbv2.ApplicationTargetGroup;

  constructor(scope: Construct, id: string, props: LoadBalancerConstructProps) {
    super(scope, id);

    const { vpc } = props;

    this.loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'LoadBalancer', {
      vpc,
      internetFacing: true,
    });

    this.targetGroup = new elbv2.ApplicationTargetGroup(this, 'TargetGroup', {
      vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
    });

    this.targetGroup.configureHealthCheck({
      path: '/internal/meta/health',
      protocol: elbv2.Protocol.HTTP,
      port: '8080',
      healthyHttpCodes: '200',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(5),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 5,
    });

    const listener = this.loadBalancer.addListener('Listener', {
      port: 80,
      open: true,
    });

    listener.addAction('ForwardToTargetGroup', {
      action: elbv2.ListenerAction.forward([this.targetGroup]),
    });

    const outputs = [
      {
        id: 'LoadBalancerDNSName',
        value: this.loadBalancer.loadBalancerDnsName,
        description: 'The DNS name of the load balancer',
      },
      {
        id: 'TargetGroupArn',
        value: this.targetGroup.targetGroupArn,
        description: 'The ARN of the target group',
      },
      {
        id: 'VpcId',
        value: vpc.vpcId,
        description: 'The ID of the VPC',
      },
      {
        id: 'PublicSubnetIds',
        value: vpc.publicSubnets.map(subnet => subnet.subnetId).join(','),
        description: 'The IDs of the public subnets',
      },
      {
        id: 'ListenerArn',
        value: listener.listenerArn,
        description: 'The ARN of the load balancer listener',
      },
    ];

    outputs.forEach(output =>
      new cdk.CfnOutput(this, output.id, {
        value: output.value,
        description: output.description,
      })
    );

  }
}
