package com.myorg;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

public class ParticipationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static final Logger logger = Logger.getLogger(ParticipationHandler.class.getName());
    private final S3Client s3Client;
    private final RekognitionClient rekognitionClient;
    private final TextractClient textractClient;
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;

    public ParticipationHandler() {
        this.s3Client = S3Client.create();
        this.rekognitionClient = RekognitionClient.create();
        this.textractClient = TextractClient.create();
        this.dynamoDbClient = DynamoDbClient.create();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> response = new HashMap<>();
        response.put("headers", Map.of("Access-Control-Allow-Origin", "*", "Content-Type", "application/json"));
        response.put("statusCode", 200);

        try {
            // Extract body from API Gateway event
            String body = (String) event.get("body");
            if (body == null) {
                throw new IllegalArgumentException("Request body is missing");
            }

            // Parse body as JSON
            Map<String, String> input = objectMapper.readValue(body, Map.class);
            String name = input.get("name");
            String email = input.get("email");
            String date = input.get("date");
            String faceImageBase64 = input.get("face_image");

            // Validate input
            if (name == null || email == null || date == null || faceImageBase64 == null) {
                throw new IllegalArgumentException("Missing required fields: name, email, date, or face_image");
            }

            // Environment variables
            String bucketName = System.getenv("S3_BUCKET");
            String namesImageKey = System.getenv("NAMES_IMAGE");
            String facesImageKey = System.getenv("FACES_IMAGE");
            String tableName = System.getenv("TABLE_NAME");

            // Upload face image to S3
            String uploadedFaceKey = "proj3/uploaded_face_" + System.currentTimeMillis() + ".jpg";
            byte[] faceImageBytes = Base64.getDecoder().decode(faceImageBase64);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uploadedFaceKey)
                    .build(), RequestBody.fromBytes(faceImageBytes));

            // Name matching with Textract
            boolean nameMatch = false;
            GetObjectRequest namesImageRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(namesImageKey)
                    .build();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            s3Client.getObject(namesImageRequest).transferTo(baos);
            byte[] namesImageBytes = baos.toByteArray();

            DetectDocumentTextRequest textractRequest = DetectDocumentTextRequest.builder()
                    .document(d -> d.bytes(SdkBytes.fromByteArray(namesImageBytes)))
                    .build();
            DetectDocumentTextResponse textractResponse = textractClient.detectDocumentText(textractRequest);
            StringBuilder extractedText = new StringBuilder();
            textractResponse.blocks().stream()
                    .filter(block -> "LINE".equals(block.blockTypeAsString()))
                    .forEach(block -> extractedText.append(block.text()).append(" "));
            nameMatch = extractedText.toString().toLowerCase().contains(name.toLowerCase());
            logger.info("Name matched: " + nameMatch);

            // Face matching with Rekognition
            boolean faceMatch = false;
            Image sourceImage = Image.builder()
                    .bytes(SdkBytes.fromByteArray(faceImageBytes))
                    .build();
            Image targetImage = Image.builder()
                    .s3Object(s -> s.bucket(bucketName).name(facesImageKey))
                    .build();
            CompareFacesRequest compareFacesRequest = CompareFacesRequest.builder()
                    .sourceImage(sourceImage)
                    .targetImage(targetImage)
                    .similarityThreshold(85.0f)
                    .build();
            CompareFacesResponse compareFacesResponse = rekognitionClient.compareFaces(compareFacesRequest);
            faceMatch = !compareFacesResponse.faceMatches().isEmpty();
            logger.info("Face match result: " + faceMatch);

            // Participation result
            boolean participation = nameMatch && faceMatch;

            // Store in DynamoDB
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("email", AttributeValue.builder().s(email).build());
            item.put("date", AttributeValue.builder().s(date).build());
            item.put("name", AttributeValue.builder().s(name).build());
            item.put("participation", AttributeValue.builder().bool(participation).build());
            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();
            dynamoDbClient.putItem(putItemRequest);
            logger.info("Stored participation record for email: " + email);

            // Prepare response
            Map<String, Object> result = new HashMap<>();
            result.put("participation", participation);
            result.put("name_match", nameMatch);
            result.put("face_match", faceMatch);
            response.put("body", objectMapper.writeValueAsString(result));

        } catch (Exception e) {
            logger.severe("Error processing request: " + e.getMessage());
            response.put("statusCode", 500);
            response.put("body", "{\"error\":\"" + e.getMessage() + "\"}");
        }

        return response;
    }
}