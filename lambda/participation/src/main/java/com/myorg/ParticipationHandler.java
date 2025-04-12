package com.myorg;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParticipationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = LogManager.getLogger(ParticipationHandler.class);
    private final S3Client s3Client = S3Client.create();
    private final RekognitionClient rekognitionClient = RekognitionClient.create();
    private final TextractClient textractClient = TextractClient.create();
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String bucket = System.getenv("S3_BUCKET");
    private final String namesImage = System.getenv("NAMES_IMAGE");
    private final String facesImage = System.getenv("FACES_IMAGE");
    private final String tableName = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        response.setHeaders(headers);

        try {
            // Parse request body
            Map<String, String> body = objectMapper.readValue(event.getBody(), Map.class);
            String name = body.get("name");
            String email = body.get("email");
            String date = body.get("date");
            String base64Image = body.get("face_image");

            // Decode and upload image to S3
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
            String imageKey = "proj3/" + UUID.randomUUID().toString() + ".jpg";
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(imageKey)
                    .contentType("image/jpeg")
                    .build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(imageBytes));

            // Check name match with Textract
            boolean nameMatch = checkNameMatch(name);
            // Check face match with Rekognition
            boolean faceMatch = checkFaceMatch(imageKey);
            boolean participation = nameMatch || faceMatch;

            // Store in DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("email", AttributeValue.builder().s(email).build());
            item.put("date", AttributeValue.builder().s(date).build());
            item.put("name", AttributeValue.builder().s(name).build());
            item.put("participation", AttributeValue.builder().bool(participation).build());
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            // Prepare response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("participation", participation);
            responseBody.put("name_match", nameMatch);
            responseBody.put("face_match", faceMatch);
            response.setStatusCode(200);
            response.setBody(objectMapper.writeValueAsString(responseBody));
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            response.setStatusCode(500);
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
        return response;
    }

    private boolean checkNameMatch(String name) {
        try {
            DetectDocumentTextResponse response = textractClient.detectDocumentText(
                    DetectDocumentTextRequest.builder()
                            .document(software.amazon.awssdk.services.textract.model.Document.builder()
                                    .s3Object(software.amazon.awssdk.services.textract.model.S3Object.builder()
                                            .bucket(bucket)
                                            .name(namesImage)
                                            .build())
                                    .build())
                            .build());
            for (software.amazon.awssdk.services.textract.model.Block block : response.blocks()) {
                if (block.blockTypeAsString().equals("LINE") && name.equalsIgnoreCase(block.text())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Textract error: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean checkFaceMatch(String sourceImageKey) {
        try {
            CompareFacesResponse response = rekognitionClient.compareFaces(
                    CompareFacesRequest.builder()
                            .sourceImage(software.amazon.awssdk.services.rekognition.model.Image.builder()
                                    .s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                            .bucket(bucket)
                                            .name(sourceImageKey)
                                            .build())
                                    .build())
                            .targetImage(software.amazon.awssdk.services.rekognition.model.Image.builder()
                                    .s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                            .bucket(bucket)
                                            .name(facesImage)
                                            .build())
                                    .build())
                            .similarityThreshold(80f)
                            .build());
            return !response.faceMatches().isEmpty();
        } catch (Exception e) {
            logger.error("Rekognition error: {}", e.getMessage(), e);
            return false;
        }
    }
}