from fastapi import FastAPI, File, UploadFile, HTTPException, Request
from fastapi.responses import PlainTextResponse
from fastapi.middleware.cors import CORSMiddleware
import os
from pathlib import Path
import threading
import time
import urllib.request
import sqlite3
import json
import io
from PIL import Image
from typing import Optional
from collections import defaultdict

BASE_DIR = Path(__file__).resolve().parent
DB_FILE = BASE_DIR / "pest_detection.db"
MODEL_FILE = BASE_DIR / "best_cereal.pt"
MODEL_IP102_FILE = BASE_DIR / "best_ip102.pt"
IP102_URL = "https://huggingface.co/underdogquality/yolo11s-pest-detection/resolve/main/best.pt"

def init_db():
    conn = sqlite3.connect(str(DB_FILE))
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS audit_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            endpoint TEXT NOT NULL,
            status TEXT NOT NULL,
            details TEXT,
            ip_address TEXT
        )
    """)
    conn.commit()
    
    # Prune audit logs older than 30 days
    try:
        cursor.execute("DELETE FROM audit_logs WHERE timestamp < datetime('now', '-30 days')")
        pruned_count = cursor.rowcount
        conn.commit()
        if pruned_count > 0:
            print(f"[DB] Successfully pruned {pruned_count} audit log entries older than 30 days.")
    except Exception as e:
        print(f"[DB] Warning: Failed to prune audit logs: {e}")
        
    conn.close()

def log_audit(username: Optional[str], endpoint: str, status: str, details: str, ip_address: str):
    try:
        conn = sqlite3.connect(str(DB_FILE))
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO audit_logs (username, endpoint, status, details, ip_address) VALUES (?, ?, ?, ?, ?)",
            (username, endpoint, status, details, ip_address)
        )
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"Failed to write audit log: {e}")

init_db()

app = FastAPI(title="Pest Detection API")

# Configure CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# In-memory IP-based rate limiting
RATE_LIMIT_WINDOW = 60  # 1 minute
RATE_LIMIT_MAX_REQUESTS = 10
request_history = defaultdict(list)

model = None
HAS_YOLO = False
is_downloading = False

def download_ip102_model():
    global is_downloading, model, HAS_YOLO
    if is_downloading:
        return
    is_downloading = True
    print("[IP102] Starting background download of IP102 model weights from Hugging Face...")
    
    retries = 5
    for attempt in range(retries):
        try:
            req = urllib.request.Request(IP102_URL, headers={'User-Agent': 'Mozilla/5.0'})
            temp_file = MODEL_IP102_FILE.with_suffix(".tmp")
            
            with urllib.request.urlopen(req, timeout=60) as response, open(temp_file, 'wb') as out_file:
                chunk_size = 1024 * 1024
                bytes_downloaded = 0
                while True:
                    chunk = response.read(chunk_size)
                    if not chunk:
                        break
                    out_file.write(chunk)
                    bytes_downloaded += len(chunk)
                    if bytes_downloaded % (5 * 1024 * 1024) == 0:
                        print(f"[IP102] Download progress: {bytes_downloaded / (1024 * 1024):.1f} MB downloaded...")
            
            if temp_file.exists() and temp_file.stat().st_size > 30 * 1024 * 1024:
                if MODEL_IP102_FILE.exists():
                    MODEL_IP102_FILE.unlink()
                temp_file.rename(MODEL_IP102_FILE)
                print(f"[IP102] Download complete: {MODEL_IP102_FILE}")
                
                from ultralytics import YOLO
                model = YOLO(str(MODEL_IP102_FILE))
                HAS_YOLO = True
                print(f"[IP102] Successfully loaded downloaded IP102 model with {len(model.names)} classes.")
                is_downloading = False
                return
        except Exception as e:
            print(f"[IP102] Error on download attempt {attempt+1}/{retries}: {e}")
            if temp_file.exists():
                try:
                    temp_file.unlink()
                except:
                    pass
            time.sleep(3)
            
    print("[IP102] Failed to download IP102 model after all retries.")
    is_downloading = False

def init_model():
    global model, HAS_YOLO
    try:
        from ultralytics import YOLO

        if MODEL_IP102_FILE.exists():
            model = YOLO(str(MODEL_IP102_FILE))
            HAS_YOLO = True
            print(f"Success: Loaded IP102 YOLO model from {MODEL_IP102_FILE} ({len(model.names)} classes)")
            return

        loaded_fallback = False
        if MODEL_FILE.exists():
            model = YOLO(str(MODEL_FILE))
            HAS_YOLO = True
            print(f"Success: Loaded fallback 10-pest YOLO model from {MODEL_FILE} ({len(model.names)} classes)")
            loaded_fallback = True
        else:
            fallback_path = Path(r"C:\Users\fadhi\runs\detect\train-3\weights\best.pt")
            if fallback_path.exists():
                model = YOLO(str(fallback_path))
                HAS_YOLO = True
                print(f"Success: Loaded fallback YOLO model from {fallback_path} ({len(model.names)} classes)")
                loaded_fallback = True

        print("IP102 model is missing. Initiating background download...")
        threading.Thread(target=download_ip102_model, daemon=True).start()
    except Exception as e:
        if not HAS_YOLO:
            HAS_YOLO = False
            print(f"Warning: Failed to load YOLO model: {e}. Using dummy detection.")
        else:
            print(f"Warning: Fallback model loaded, but error occurred: {e}")

init_model()

def normalize_key(name):
    return name.lower().replace("-", " ").replace("_", " ").strip()

def get_ip102_category_and_treatment(cls_id, raw_name):
    if 0 <= cls_id <= 13:
        return "Rice Pest", "Rice Pest: Maintain proper water levels, avoid excess nitrogen, use light traps, and introduce natural predators like ducklings or frogs. Apply specific systemic insecticides if threshold is exceeded."
    elif 14 <= cls_id <= 21:
        return "Soil/General Pest", "Soil/Root Pest: Use crop rotation, deep tillage during winter to expose pests, and apply biological control agents like entomopathogenic nematodes or soil-applied bio-pesticides."
    elif 22 <= cls_id <= 23:
        return "Corn Pest", "Corn Pest: Rotate crops with legumes, use pheromone traps, plant pest-resistant hybrids (e.g., Bt corn), and apply targeted biological sprays when larvae are young."
    elif 24 <= cls_id <= 35:
        return "Wheat Pest", "Wheat Pest: Maintain weed-free borders, practice early planting, conserve natural insect predators (like ladybugs), and apply recommended selective foliar treatments if infestation is high."
    elif 36 <= cls_id <= 43:
        return "Beet Pest", "Beet Pest: Remove weed hosts, practice crop rotation, use row covers for young crops, and apply neem-based sprays or target selective insecticides early in the cycle."
    elif 44 <= cls_id <= 55:
        return "Alfalfa Pest", "Alfalfa Pest: Harvest early if damage is visible to disrupt life cycles, use resistant varieties, and apply selective insecticides only if the economic threshold is crossed to save beneficial insects."
    elif 56 <= cls_id <= 72:
        return "Grape Pest", "Grape/Vine Pest: Prune affected leaves, use yellow sticky traps, maintain vineyard hygiene, and apply mineral oil or targeted insecticidal soaps for sucking pests."
    elif 73 <= cls_id <= 91:
        return "Citrus Pest", "Citrus Pest: Prune infested shoots, encourage beneficial predators (e.g., predatory mites or wasps), use horticultural oil sprays, and apply targeted systemic treatments if necessary."
    elif 92 <= cls_id <= 101:
        return "Mango Pest", "Mango Pest: Maintain orchard sanitation, prune dense branches to improve sunlight penetration, use sticky bands on tree trunks, and apply specific crop protection sprays during flowering."
    return "Unknown Pest", "Consult with a local agricultural officer for specific treatment advice."

PEST_TREATMENT = {
    "brown planthopper": "Avoid excessive nitrogen fertilizer. Use resistant varieties and specific insecticides like Buprofezin.",
    "brown plant hopper": "Avoid excessive nitrogen fertilizer. Use resistant varieties and specific insecticides like Buprofezin.",
    "green leafhopper": "Use light traps. Apply appropriate systemic insecticides to prevent Tungro disease transmission.",
    "rice leafhopper": "Use light traps. Apply appropriate systemic insecticides to prevent Tungro disease transmission.",
    "leaf folder": "Conserve natural enemies. Use neem-based pesticides or Cartap Hydrochloride if damage is severe.",
    "rice leaf roller": "Conserve natural enemies. Use neem-based pesticides or Cartap Hydrochloride if damage is severe.",
    "rice bug": "Maintain clean fields. Apply contact insecticides (e.g., Lambda-cyhalothrin) during early morning or late evening.",
    "stem borer": "Maintain proper field drainage, use pheromone traps, and apply recommended chemical treatments like Carbofuran.",
    "asiatic rice borer": "Maintain proper field drainage, use pheromone traps, and apply recommended chemical treatments like Carbofuran.",
    "yellow rice borer": "Maintain proper field drainage, use pheromone traps, and apply recommended chemical treatments like Carbofuran.",
    "whorl maggot": "Drain the field for a few days to reduce maggot survival. Apply appropriate foliar sprays if infestation is high.",
    "paddy stem maggot": "Drain the field for a few days to reduce maggot survival. Apply appropriate foliar sprays if infestation is high.",
    "fall army worm": "Use biological control agents (like Trichogramma wasps) or apply recommended chemical treatments like Spinetoram or Chlorantraniliprole.",
    "army worm": "Use biological control agents (like Trichogramma wasps) or apply recommended chemical treatments like Spinetoram or Chlorantraniliprole.",
    "northern corn rootworm": "Rotate crops with non-host plants (like soybeans). Use soil insecticides or crop varieties containing Bt traits.",
    "southern corn rootworm": "Practice early planting, rotate crops, and apply recommended soil insecticides during planting if history of damage exists.",
    "western corn rootworm": "Use crop rotation. Apply granular soil insecticides at planting, or plant Bt rootworm-resistant corn hybrids.",
    "aphids": "Use insecticidal soaps, neem oil, or introduce natural predators like ladybugs.",
    "none": "No treatment required. Your crops look healthy!",
    "default": "Consult with a local agricultural officer for specific treatment advice.",
}

ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg"}

def allowed_file(filename):
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_EXTENSIONS

@app.get("/", response_class=PlainTextResponse)
def index():
    model_info = "Disabled"
    if HAS_YOLO and model:
        model_info = f"Enabled ({len(model.names)} classes)"
    return f"Pest Detection API is running! YOLO Model: {model_info}"

@app.get("/health")
def health(request: Request):
    classes_count = 0
    model_type = "None"
    if HAS_YOLO and model:
        classes_count = len(model.names)
        model_type = "IP102 (102 pests)" if classes_count == 102 else "Cereal Pests (10 pests)"
        
    return {
        "status": "ok",
        "yolo": HAS_YOLO,
        "model_type": model_type,
        "classes_count": classes_count,
        "is_downloading_ip102": is_downloading
    }

@app.post("/predict")
async def predict(
    request: Request,
    image: UploadFile = File(...)
):
    ip = request.client.host if request.client else "unknown"

    # Rate limiting check (excludes development environments)
    if ip not in ("127.0.0.1", "10.0.2.2", "localhost", "unknown"):
        now = time.time()
        request_history[ip] = [t for t in request_history[ip] if now - t < RATE_LIMIT_WINDOW]
        if len(request_history[ip]) >= RATE_LIMIT_MAX_REQUESTS:
            log_audit("anonymous", "/predict", "rate-limited", f"Rate limit exceeded (IP: {ip})", ip)
            raise HTTPException(status_code=429, detail="Too many requests. Please try again later.")
        request_history[ip].append(now)

    if not image.filename:
        log_audit("anonymous", "/predict", "failed", "No selected file", ip)
        raise HTTPException(status_code=400, detail="No selected file")

    if not allowed_file(image.filename):
        log_audit("anonymous", "/predict", "failed", f"File type not allowed: {image.filename}", ip)
        raise HTTPException(status_code=400, detail="File type not allowed")

    try:
        contents = await image.read()
        
        if len(contents) > 5 * 1024 * 1024:
            log_audit("anonymous", "/predict", "failed", "File too large (exceeded 5MB)", ip)
            raise HTTPException(status_code=413, detail="File size exceeds the 5MB limit.")
            
        img = Image.open(io.BytesIO(contents)).convert("RGB")
    except HTTPException as he:
        raise he
    except Exception as e:
        log_audit("anonymous", "/predict", "error", f"Image parsing error: {str(e)}", ip)
        raise HTTPException(status_code=500, detail=f"Failed to process image: {str(e)}")

    if HAS_YOLO:
        try:
            results = model.predict(source=img, save=False, conf=0.4)

            detections = []
            for r in results:
                for box in r.boxes:
                    cls_id = int(box.cls[0])
                    raw_name = model.names[cls_id]
                    formatted_name = raw_name.replace("-", " ").title()
                    conf = float(box.conf[0])
                    
                    norm_name = normalize_key(raw_name)
                    if norm_name in PEST_TREATMENT:
                        treatment = PEST_TREATMENT[norm_name]
                    elif len(model.names) == 102:
                        _, treatment = get_ip102_category_and_treatment(cls_id, raw_name)
                    else:
                        treatment = PEST_TREATMENT["default"]

                    detections.append({
                        "pest_detected": formatted_name,
                        "confidence": round(conf, 2),
                        "treatment": treatment,
                    })

            if not detections:
                log_details = json.dumps({"pest": "None", "confidence": 0.0, "filename": image.filename})
                log_audit("anonymous", "/predict", "success", log_details, ip)
                return {
                    "status": "success",
                    "pest_detected": "None",
                    "confidence": 0.0,
                    "treatment": PEST_TREATMENT["none"],
                    "message": "No pests detected",
                }

            log_details = json.dumps({
                "pest": detections[0]["pest_detected"],
                "confidence": detections[0]["confidence"],
                "filename": image.filename
            })
            log_audit("anonymous", "/predict", "success", log_details, ip)

            return {
                "status": "success",
                "pest_detected": detections[0]["pest_detected"],
                "confidence": detections[0]["confidence"],
                "treatment": detections[0]["treatment"],
                "message": f"Detected {len(detections)} pests",
                "all_detections": detections,
            }
        except Exception as e:
            log_audit("anonymous", "/predict", "error", f"YOLO Inference error: {str(e)}", ip)
            raise HTTPException(status_code=500, detail=f"Inference error: {str(e)}")
    else:
        log_details = json.dumps({"pest": "Aphids (Mock)", "confidence": 0.95, "filename": image.filename})
        log_audit("anonymous", "/predict", "success", log_details, ip)
        return {
            "status": "success",
            "pest_detected": "Aphids (Mock)",
            "confidence": 0.95,
            "treatment": PEST_TREATMENT["aphids"],
            "message": "Detection completed successfully (Mock Mode)",
        }

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 5000))
    uvicorn.run(app, host="0.0.0.0", port=port)
