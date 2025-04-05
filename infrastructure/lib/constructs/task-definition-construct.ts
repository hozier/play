import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

const secretJson = process.env.SECRET_JSON;

if (!secretJson) {
  throw new Error('SECRET_JSON environment variable is not set');
}

interface TaskDefinitionConstructProps {
  repository: ecr.IRepository;
  imageTag: string;
}

export class TaskDefinitionConstruct extends Construct {
  public readonly taskDefinition: ecs.FargateTaskDefinition;

  constructor(scope: Construct, id: string, props: TaskDefinitionConstructProps) {
    super(scope, id);

    const { repository, imageTag } = props;

    const taskExecutionRole = new iam.Role(this, 'TaskExecutionRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    taskExecutionRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')
    );

    this.taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDefinition', {
      memoryLimitMiB: 8192,
      cpu: 4096,
      executionRole: taskExecutionRole,
    });

    const container = this.taskDefinition.addContainer('AppContainer', {
      image: ecs.ContainerImage.fromEcrRepository(repository, imageTag),
      memoryReservationMiB: 2048,
      healthCheck: {
        timeout: cdk.Duration.seconds(5),
        interval: cdk.Duration.seconds(15),
        retries: 3,
        command: ['CMD-SHELL', 'curl -f http://localhost:8080/internal/meta/health || exit 1'],
        startPeriod: cdk.Duration.seconds(30),
      },
      logging: ecs.LogDriver.awsLogs({
        streamPrefix: 'AppContainerLogs',
      }),
      environment: {
        NODE_ENV: 'production',
        GOOGLE_APPLICATION_CREDENTIALS: '/secrets/credentials.json', // Path for the credentials file
        SECRET_JSON: secretJson!, // Pass the secret JSON directly as an environment variable
      },
    });

    container.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // Add a volume for the credentials file
    this.taskDefinition.addVolume({
      name: 'SecretsVolume',
      host: {}, // Use an emptyDir volume
    });

    container.addMountPoints({
      containerPath: '/secrets',
      sourceVolume: 'SecretsVolume',
      readOnly: false,
    });
  }
}
