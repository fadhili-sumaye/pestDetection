from ultralytics import YOLO
import os

import torch

# 1. Load the base YOLOv8 Medium model (better capacity for feature learning)
model = YOLO('yolov8m.pt') 

# 2. IMPORTANT: Path to the 'data.yaml' file inside the unzipped dataset.
dataset_yaml_path = r'c:\Users\fadhi\StudioProjects\pestDetection\datasets\cereal_pests\data.yaml'

def start_training():
    if not os.path.exists(dataset_yaml_path):
        print(f"Error: Could not find data.yaml at {dataset_yaml_path}")
        print("Please edit train_model.py and put the correct path to your unzipped dataset.")
        return

    print("--- Starting AI Training for Cereal Pests ---")
    
    # Automatically detect if NVIDIA GPU (CUDA) is available for 10x-50x faster training
    device = 0 if torch.cuda.is_available() else 'cpu'
    print(f"Using device: {device} ({'GPU' if device == 0 else 'CPU'})")
    
    # Train the model
    # epochs=50: The AI will study the images 50 times.
    # imgsz=640: Standard resolution for YOLOv8.
    results = model.train(
        data=dataset_yaml_path, 
        epochs=50, 
        imgsz=640, 
        device=device,
        batch=16,             # Stable batch size (reduce to 8 or 4 if GPU runs out of memory)
        freeze=10,            # Freeze backbone layers to prevent overfitting on small datasets
        weight_decay=0.005,   # Weight decay (L2 regularization) to improve generalization
        close_mosaic=10       # Turn off mosaic augmentation for the last 10 epochs for stable bounding boxes
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
        backend_model_path = backend_dir / "best_cereal.pt"
        
        if best_model_path.exists():
            backend_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(best_model_path, backend_model_path)
            print(f"\n[AUTO-COPY] Successfully copied {best_model_path} to {backend_model_path}")
        else:
            print(f"\nWarning: Could not locate best.pt at {best_model_path}")
    except Exception as e:
        print(f"\nWarning: Failed to auto-copy trained model to backend: {e}")
        print("Please copy the best.pt file manually as best_cereal.pt in the backend/ folder.")

    print("\nNext step: Start your backend server and run the mobile app.")

if __name__ == '__main__':
    start_training()
