import * as cdk from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { ServiceStack } from '../lib/service-stack';
import { EcsServiceStack } from '../lib/ecs-service-stack';

test('ServiceStack creates resources', () => {
  const app = new cdk.App();
  const stack = new ServiceStack(app, 'TestServiceStack');
  const template = Template.fromStack(stack);

  template.resourceCountIs('AWS::ECS::Cluster', 1);
  template.resourceCountIs('AWS::EC2::VPC', 1);
});

test('EcsStack creates resources', () => {
  const app = new cdk.App();
  const stack = new EcsServiceStack(app, 'TestEcsStack');
  const template = Template.fromStack(stack);

  template.resourceCountIs('AWS::IAM::Role', 1);
  template.resourceCountIs('AWS::ECS::TaskDefinition', 1);
});
