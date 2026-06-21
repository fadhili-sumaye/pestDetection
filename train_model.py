from ultralytics import YOLO
import os

# 1. Load the base YOLOv8 model (this is the "starting point")
model = YOLO('yolov8n.pt') 

# 2. IMPORTANT: Path to the 'data.yaml' file inside the unzipped dataset.
dataset_yaml_path = r'c:\Users\fadhi\StudioProjects\pestDetection\datasets\cereal_pests\data.yaml'

def start_training():
    if not os.path.exists(dataset_yaml_path):
        print(f"Error: Could not find data.yaml at {dataset_yaml_path}")
        print("Please edit train_model.py and put the correct path to your unzipped dataset.")
        return

    print("--- Starting AI Training for Cereal Pests ---")
    
    # Train the model
    # epochs=50: The AI will study the images 50 times.
    # imgsz=640: Standard resolution for YOLOv8.
    # device='cpu': Uses your computer processor (change to '0' if you have an NVIDIA GPU).
    results = model.train(
        data=dataset_yaml_path, 
        epochs=10, 
        imgsz=640, 
        device='cpu'
    )
    
    print("\nSUCCESS!")
    print("Your custom pest detection model has been created.")
    
    # Auto-copy the trained model to the backend folder to prevent manual errors/confusion
    try:
        import shutil
        from pathlib import Path
        
        save_dir = Path(results.save_dir)
        best_model_path = save_dir / "weights" / "best.pt"
        
        backend_dir = Path(__file__).resolve().parent / "backend"
        backend_model_path = backend_dir / "best.pt"
        
        if best_model_path.exists():
            backend_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(best_model_path, backend_model_path)
            print(f"\n[AUTO-COPY] Successfully copied {best_model_path} to {backend_model_path}")
        else:
            print(f"\nWarning: Could not locate best.pt at {best_model_path}")
    except Exception as e:
        print(f"\nWarning: Failed to auto-copy trained model to backend: {e}")
        print("Please copy the best.pt file manually to the backend/ folder.")

    print("\nNext step: Start your backend server and run the mobile app.")

if __name__ == '__main__':
    start_training()
