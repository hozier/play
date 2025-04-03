import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

export class EcsServiceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Create a VPC
    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
    });

    // Create an ECS cluster
    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc,
    });

    // Reference the ECR repository
    const repository = ecr.Repository.fromRepositoryName(this, 'EcrRepository', 'theproductcollectiveco/play4s-service-prod');

    // Create the task execution role
    const taskExecutionRole = new iam.Role(this, 'TaskExecutionRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    // Attach the AmazonECSTaskExecutionRolePolicy
    taskExecutionRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')
    );

    // Add explicit permissions for ECR
    taskExecutionRole.addToPolicy(new iam.PolicyStatement({
      actions: [
        'ecr:GetAuthorizationToken',
        'ecr:BatchCheckLayerAvailability',
        'ecr:GetDownloadUrlForLayer',
        'ecr:DescribeRepositories',
        'ecr:BatchGetImage',
      ],
      resources: ['*'], // allow actions on all resources // [repository.repositoryArn], // Use specific ECR repository ARN
    }));

    // Create a CloudWatch log group
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      retention: logs.RetentionDays.ONE_WEEK,
    });

    // Create the task definition
    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDefinition', {
      memoryLimitMiB: 2048,
      cpu: 1024,
      executionRole: taskExecutionRole,
    });

    // Add the container to the task definition
    const container = taskDefinition.addContainer('AppContainer', {
      image: ecs.ContainerImage.fromEcrRepository(repository, 'latest'),
      memoryReservationMiB: 256,
      logging: ecs.LogDriver.awsLogs({
        streamPrefix: 'AppContainerLogs',
        logGroup,
      }),
      environment: {
        NODE_ENV: 'production',
      },
    });

    // Add port mappings to the container
    container.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // Create a security group for the Fargate service
    const serviceSecurityGroup = new ec2.SecurityGroup(this, 'ServiceSecurityGroup', {
      vpc,
    });

    // Allow traffic from the load balancer to the Fargate service
    serviceSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(), // Allow traffic from the load balancer
      ec2.Port.tcp(8080),
      'Allow traffic from ALB to Fargate service'
    );

    // Create an Application Load Balancer
    const loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'LoadBalancer', {
      vpc,
      internetFacing: true,
    });

    // Create a security group for the load balancer
    const loadBalancerSecurityGroup = loadBalancer.connections.securityGroups[0];

    // Allow traffic from the load balancer to the Fargate service
    loadBalancerSecurityGroup.addEgressRule(
      ec2.Peer.securityGroupId(serviceSecurityGroup.securityGroupId),
      ec2.Port.tcp(8080),
      'Allow traffic from ALB to Fargate service'
    );

    const targetGroup = new elbv2.ApplicationTargetGroup(this, 'TargetGroup', {
      vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
    });

    // Add a health check to the target group
    targetGroup.configureHealthCheck({
      path: '/internal/meta/health',
      port: '8080',
      protocol: elbv2.Protocol.HTTP,
      healthyHttpCodes: '200',
    });

    const listener = loadBalancer.addListener('Listener', {
      port: 80,
      open: true,
    });

    listener.addTargetGroups('DefaultTargetGroup', {
      targetGroups: [targetGroup],
    });

    const fargateService = new ecs.FargateService(this, 'FargateService', {
      cluster,
      taskDefinition,
      assignPublicIp: true,
      securityGroups: [serviceSecurityGroup],
      desiredCount: 1,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // Attach the Fargate service to the target group
    fargateService.attachToApplicationTargetGroup(targetGroup);
  }
}