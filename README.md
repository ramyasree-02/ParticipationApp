# 🎯 Participation Verification App (Serverless on AWS)

## 📌 Introduction  
This project is a lightweight serverless application designed to confirm event participation by cross-verifying user-provided details (name + face image) with stored records using AWS cloud services. It leverages AWS’s AI and storage services to deliver a seamless and automated verification flow.  

---

## 👥 Team – Group 13  
- **Ramya Sree Salividi** – rsalividi@uco.edu  
- **Anand Kumar Gedala** – agedala@uco.edu  

---

## 🔍 How It Works  
Participants submit their **name, email, event date, and a photo** through a simple web interface. Once submitted, the system:  

1. 📄 Extracts participant names from an uploaded file in **Amazon S3** using **Textract**.  
2. 🖼️ Performs **facial recognition** by comparing the uploaded photo against a stored faces dataset with **Amazon Rekognition**.  
3. 🗄️ Saves verification results in a **DynamoDB table (proj3-participation)**.  
4. 🌐 Exposes an endpoint `/process-image` via **API Gateway** for processing submissions.  
5. 🎨 Delivers a static frontend hosted on **AWS Amplify** for easy access.  

---

## ✨ Key Features  
- **Frontend:** Simple HTML form hosted on Amplify for user submissions.  
- **Backend:** AWS **Lambda function** (`Proj3ParticipationFunction`) implemented in **Java 21**.  
- **API:** REST endpoint (`POST /process-image`) with **CORS** enabled.  
- **Storage:** S3 bucket (`group13awsbucket`) for files + DynamoDB for results.  
- **CI/CD:** Automated build and deployment via **GitHub Actions** and **AWS CDK**.  

---

## 🚀 Setup Instructions  

1. Clone the repository:  
   ```bash
   git clone https://github.com/ramyasree-02/ParticipationApp
   cd ParticipationApp
   ```  

2. Deploy the stack with AWS CDK (Java).  
3. Push changes to GitHub → GitHub Actions will handle build & deploy automatically.  
4. Access the frontend via the Amplify-hosted URL.  

---

## 📖 Acknowledgement  
This project was collaboratively developed as part of **CMSC 4223/5223 – Cyber Infrastructure and Cloud Computing (Project 3)** coursework.  
