import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as cr from 'aws-cdk-lib/custom-resources';
import { Construct } from 'constructs';
import { APP_NAME, AWS_ENV } from '../../config/environments';

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

    const googleCredentialsSecret = secretsmanager.Secret.fromSecretNameV2(
      this,
      'GoogleCredentialsSecret',
      'google-credentials-key'
    );

    const imageDigestResource = new cr.AwsCustomResource(this, 'ImageDigestResource', {
      onCreate: {
        service: 'ECR',
        action: 'describeImages',
        parameters: {
          repositoryName: repository.repositoryName,
          imageIds: [{ imageTag }],
        },
        physicalResourceId: cr.PhysicalResourceId.of(`${repository.repositoryName}:${imageTag}`),
      },
      policy: cr.AwsCustomResourcePolicy.fromSdkCalls({ resources: [repository.repositoryArn] }),
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
        GOOGLE_APPLICATION_CREDENTIALS: '/tmp/secrets/credentials.json',
        AWS_ENV: AWS_ENV,
        APP_NAME: APP_NAME,
        IMAGE_DIGEST: imageDigestResource.getResponseField('imageDetails.0.imageDigest'),
      },
      secrets: {
        CREDENTIALS_JSON: ecs.Secret.fromSecretsManager(
          googleCredentialsSecret,
          'CREDENTIALS_JSON' // Extract only the value of the CREDENTIALS_JSON key
        ),
      },
    });

    container.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });
  }
}
