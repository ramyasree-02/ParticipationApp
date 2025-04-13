package com.myorg;

import java.util.Arrays;
import java.util.HashMap;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

public class Proj3ParticipationAppStack extends Stack {
    public Proj3ParticipationAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // S3 Bucket (existing)
        IBucket bucket = Bucket.fromBucketName(this, "Proj3Bucket", "group13awsbucket");

        // DynamoDB Table (existing)
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
                    put("NAMES_IMAGE", "proj3/names.jpg");
                    put("FACES_IMAGE", "proj3/faces.jpg");
                    put("TABLE_NAME", table.getTableName());
                }})
                .build();

        // Permissions
        bucket.grantReadWrite(lambdaFunction);
        table.grantReadWriteData(lambdaFunction);
        lambdaFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("rekognition:CompareFaces", "textract:DetectDocumentText"))
                .resources(Arrays.asList("*"))
                .build());

        // API Gateway — manual route setup
        RestApi api = RestApi.Builder.create(this, "Proj3Api")
                .restApiName("proj3-participation-api")
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Arrays.asList("*"))
                        .allowMethods(Arrays.asList("POST", "OPTIONS"))
                        .allowHeaders(Arrays.asList("Content-Type", "Authorization"))
                        .build())
                .deployOptions(StageOptions.builder().stageName("dev").build())
                .build();

        // /process-image POST
        api.getRoot().addResource("process-image")
                .addMethod("POST", LambdaIntegration.Builder.create(lambdaFunction).build(),
                        MethodOptions.builder().build());

        // Output clean API endpoint
        CfnOutput.Builder.create(this, "ApiEndpoint")
                .description("API Gateway Endpoint")
                .value(api.getUrl() + "process-image")
                .build();
    }
}
