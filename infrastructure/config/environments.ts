export const SERVICE_NAME = "play4s-service-prod";

export const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION,
  IMAGE_TAG_OR_DIGEST: process.env.IMAGE_TAG_OR_DIGEST || "latest", // Default to "latest" if not provided
};