import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as iam from 'aws-cdk-lib/aws-iam';
import { RemovalPolicy } from 'aws-cdk-lib';
import { TagMutability } from 'aws-cdk-lib/aws-ecr';
import { ArnPrincipal } from 'aws-cdk-lib/aws-iam';
import { SERVICE_NAME, ORGANIZATION } from '../../config/environments';

export interface PrerequisitesStackProps extends cdk.StackProps {
  actionRoleArn: string;
}

export class PrerequisitesStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly iamRole: iam.Role;
  public readonly ecrRepo: ecr.Repository;

  constructor(scope: cdk.App, id: string, props: PrerequisitesStackProps) {
    super(scope, id, props);

    const { actionRoleArn } = props;

    this.vpc = new ec2.Vpc(this, 'Vpc', { maxAzs: 2 });

    this.iamRole = new iam.Role(this, 'IamRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      roleName: `${ORGANIZATION}-${SERVICE_NAME}-role`,
    });

    this.iamRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')
    );

    this.ecrRepo = new ecr.Repository(this, 'EcrRepo', {
      repositoryName: `${ORGANIZATION}/app-${SERVICE_NAME}-repository`,
      imageTagMutability: TagMutability.IMMUTABLE,
      imageScanOnPush: true,
      removalPolicy: RemovalPolicy.DESTROY, // Adjust for production use
    });

    this.ecrRepo.grantPullPush(new ArnPrincipal(actionRoleArn));
  }
}