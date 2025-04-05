import * as cdk from 'aws-cdk-lib';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface TaskDefinitionConstructProps {
  repository: ecr.IRepository;
  imageTag: string; // Change property name to reflect digest usage
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

    // Reference the Google Cloud credentials secret
    const googleCredentialsSecret = secretsmanager.Secret.fromSecretNameV2(
      this,
      'GoogleCredentialsSecret',
      'google-credentials-key'
    );

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
      },
      secrets: {
        CREDENTIALS_JSON: ecs.Secret.fromSecretsManager(googleCredentialsSecret), // Inject the secret
      },
      command: [
        'sh',
        '-c',
        `
        # Ensure the CREDENTIALS_JSON environment variable is set
        if [ -z "$CREDENTIALS_JSON" ]; then
          echo "Error: CREDENTIALS_JSON is not set" >&2
          exit 1
        fi && \
        # Create the /secrets directory if it doesn't exist
        mkdir -p /secrets && \
        # Write the CREDENTIALS_JSON environment variable to /secrets/credentials.json
        echo "$CREDENTIALS_JSON" > /secrets/credentials.json || exit 1
        `,
      ],
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
