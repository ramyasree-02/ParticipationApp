package com.myorg;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;
import java.util.Arrays;
import java.util.HashMap;

public class Proj3ParticipationAppStack extends Stack {
    public Proj3ParticipationAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // S3 Bucket
        Bucket bucket = Bucket.Builder.create(this, "Proj3ImagesBucket")
                .bucketName("proj3-images-" + System.currentTimeMillis())
                .versioned(false)
                .build();

        // DynamoDB Table
        Table table = Table.Builder.create(this, "Proj3ParticipationTable")
                .partitionKey(Attribute.builder().name("email").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("date").type(AttributeType.STRING).build())
                .tableName("proj3-participation")
                .build();

        // Lambda Function
        Function lambdaFunction = Function.Builder.create(this, "Proj3ParticipationFunction")
                .runtime(Runtime.JAVA_21)
                .code(Code.fromAsset("./lambda/participation/target/participation.jar"))
                .handler("com.myorg.ParticipationHandler::handleRequest")
                .memorySize(1024)
                .timeout(Duration.seconds(60))
                .environment(new HashMap<String, String>() {{
                    put("S3_BUCKET", bucket.getBucketName());
                    put("NAMES_IMAGE", "name_image.jpg");
                    put("FACES_IMAGE", "face_image.jpg");
                    put("TABLE_NAME", table.getTableName());
                }})
                .build();

        // Grant Lambda permissions
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

        // API Gateway
        LambdaRestApi api = LambdaRestApi.Builder.create(this, "Proj3Api")
                .restApiName("proj3-participation-api")
                .handler(lambdaFunction)
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Arrays.asList("*"))
                        .allowMethods(Arrays.asList("POST", "OPTIONS"))
                        .allowHeaders(Arrays.asList("Content-Type"))
                        .build())
                .deployOptions(software.amazon.awscdk.services.apigateway.StageOptions.builder()
                        .stageName("dev")
                        .build())
                .build();

        api.getRoot().addResource("process-image").addMethod("POST", LambdaIntegration.Builder.create(lambdaFunction).build());

        // Output API Endpoint
        CfnOutput.Builder.create(this, "ApiEndpoint")
                .description("API Gateway Endpoint")
                .value(api.getUrl() + "process-image")
                .build();
    }
}