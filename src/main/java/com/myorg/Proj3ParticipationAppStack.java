package com.myorg;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.HashMap;

public class Proj3ParticipationAppStack extends Stack {
    public Proj3ParticipationAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Reference existing S3 Bucket as IBucket
        IBucket bucket = Bucket.fromBucketName(this, "Group13Bucket", "group13awsbucket");

        // Reference existing DynamoDB Table as ITable
        ITable table = Table.fromTableName(this, "Proj3ParticipationTable", "proj3-participation");

        // Lambda Function
        Function lambdaFunction = Function.Builder.create(this, "Proj3ParticipationFunction")
                .runtime(Runtime.JAVA_21)
                .code(Code.fromAsset("./lambda/participation/target/participation.jar"))
                .handler("com.myorg.ParticipationHandler::handleRequest")
                .memorySize(1024)
                .timeout(Duration.seconds(60))
                .environment(new HashMap<String, String>() {{
                    put("S3_BUCKET", bucket.getBucketName());
                    put("NAMES_IMAGE", "proj3/name_image.jpg");
                    put("FACES_IMAGE", "proj3/face_image.jpg");
                    put("TABLE_NAME", table.getTableName());
                }})
                .build();

        // Grant permissions to the Lambda function
        bucket.grantReadWrite(lambdaFunction);
        table.grantReadWriteData(lambdaFunction);
        lambdaFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "rekognition:CompareFaces",
                        "textract:DetectDocumentText"
                ))
                .resources(Arrays.asList("*"))
                .build());

        // API Gateway - only /process-image POST route
        RestApi api = RestApi.Builder.create(this, "Proj3Api")
                .restApiName("proj3-participation-api")
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Arrays.asList("*"))
                        .allowMethods(Arrays.asList("POST", "OPTIONS"))
                        .allowHeaders(Arrays.asList("Content-Type"))
                        .build())
                .deployOptions(StageOptions.builder()
                        .stageName("dev")
                        .build())
                .build();

        Resource processImage = api.getRoot().addResource("process-image");
        processImage.addMethod("POST", LambdaIntegration.Builder.create(lambdaFunction).build());

        // Output API Endpoint
        CfnOutput.Builder.create(this, "ApiEndpoint")
                .description("API Gateway Endpoint")
                .value(api.getUrl() + "process-image")
                .build();
    }
}