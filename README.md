# Proj3 Participation App

A simple AWS serverless app to verify event participation by matching names and faces using AWS services.

## Overview
Users submit their name, email, date, and a face image via a web form. The app:
- Checks names against `proj3/names.jpg` (S3) using Textract.
- Matches faces against `proj3/faces.jpg` (S3) using Rekognition.
- Stores results in DynamoDB (`proj3-participation`).
- Exposes an API (`/process-image`) via API Gateway.
- Hosts the frontend on Amplify.

## Features
- Frontend: HTML form for submissions.
- Backend: Lambda function (`Proj3ParticipationFunction`) in Java 21.
- API: `POST /process-image` with CORS support.
- Storage: S3 (`group13awsbucket`), DynamoDB.
- CI/CD: GitHub Actions, AWS CDK.

## Setup
1. **Clone**:
   ```bash
   git clone https://github.com/Anand0213/proj3-participation-app
   cd proj3-participation-app