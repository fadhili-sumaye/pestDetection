---
title: Pest Detection API
emoji: 🌾
colorFrom: green
colorTo: gray
sdk: docker
app_port: 7860
pinned: false
---

# 🌾 Pest Detection API & Mobile App

A complete mobile application and Dockerized FastAPI backend system utilizing **YOLOv8** to scan, identify, and recommend treatment plans for agricultural crop pests. It supports a 10-class Cereal Pests model (Rice & Maize) and can dynamically fall back or load the full 102-class IP102 dataset model.

---

## 🚀 Deployment Guide

To make the app usable anywhere, you can deploy the backend to cloud platforms like **Hugging Face Spaces** or **Render**.

### Option A: Deploy to Hugging Face Spaces (Recommended & Free)

Hugging Face Spaces provides a free Docker hosting platform that runs 24/7.

#### Step 1: Create a Space on Hugging Face
1. Log in to your account at [Hugging Face](https://huggingface.co/).
2. Click on **New Space** (or go to `huggingface.co/new-space`).
3. Set the **Space Name** (e.g., `pest-detection-api`).
4. Select **Docker** as the SDK.
5. Choose **Blank** (or any Docker template) as the template.
6. Set the Space visibility (Public is required for your app to connect easily).
7. Click **Create Space**.

#### Step 2: Push your Code to the Space
You can deploy your code using Git:

```bash
# 1. Initialize git if you haven't (or use your existing repository)
git init

# 2. Add the Hugging Face Space repository as a remote
# Replace username and space-name with your HF details
git remote add hf https://huggingface.co/spaces/<your-username>/<space-name>

# 3. Add files and commit
git add .
git commit -m "Configure deployment files for Hugging Face"

# 4. Push to Hugging Face (usually to the main branch)
git push -f hf main
```

> [!NOTE]
> Hugging Face will automatically read the root `Dockerfile` and build/deploy your API. Once deployed, your URL will look like:
> `https://<your-username>-<space-name>.hf.space`

---

### Option B: Deploy to Render

Render is a robust, developer-friendly cloud hosting platform.

#### Step 1: Create a Web Service on Render
1. Sign up/log in to [Render](https://render.com/).
2. Click **New +** and select **Web Service**.
3. Connect your GitHub repository containing this project.
4. Set the following configuration details:
   * **Name**: `pest-detection-api`
   * **Language**: `Docker` (Render will automatically detect the root `Dockerfile`)
   * **Branch**: `main`
   * **Region**: Choose the closest region to your users
   * **Instance Type**: Free (or Starter for faster performance/no sleep times)
5. Click **Create Web Service**.

#### Step 2: Render Environmental Variables
No extra variables are strictly required, but you can customize the execution port if needed. By default, Render overrides the `PORT` environment variable and binds it automatically.

> [!IMPORTANT]
> The Free Tier of Render will "spin down" (go to sleep) if it receives no traffic for 15 minutes. The next request will take 30-50 seconds to wake the server up.

---

## 📱 Updating the Mobile App to Connect to the Cloud

Once your backend is deployed on either platform, you need to point your Android app to the new production URL.

1. Open your Android project files in Android Studio.
2. Locate the [ApiConstants.java](file:///c:/Users/fadhi/StudioProjects/pestDetection/app/src/main/java/com/example/pestdetection/ApiConstants.java) file:
   `app/src/main/java/com/example/pestdetection/ApiConstants.java`
3. Update the `DEFAULT_API_BASE_URL` constant with your production URL:

   ```java
   // Example for Hugging Face Spaces:
   public static final String DEFAULT_API_BASE_URL = "https://<your-username>-<space-name>.hf.space";
   
   // Example for Render:
   // public static final String DEFAULT_API_BASE_URL = "https://pest-detection-api.onrender.com";
   ```

4. Build and run your Android App! You can now test scanning agricultural pests from any device anywhere in the country.

---

## 🛠️ Local Development & Testing

If you want to run the backend API locally for development:

1. Navigate to the `backend/` directory:
   ```bash
   cd backend
   ```
2. Install Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Run the development server:
   ```bash
   python app.py
   ```
4. Access the API locally at `http://localhost:5000` or check the health page at `http://localhost:5000/health`.
