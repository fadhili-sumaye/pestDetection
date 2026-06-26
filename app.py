import os
import sys
from pathlib import Path

# Add backend directory to sys.path
backend_dir = Path(__file__).resolve().parent / "backend"
sys.path.insert(0, str(backend_dir))

# Change directory to backend so that databases, models and uploads are created in the right folder
os.chdir(str(backend_dir))

if __name__ == "__main__":
    import uvicorn
    # Import the FastAPI instance from app.py in backend
    from app import app
    port = int(os.environ.get("PORT", 5000))
    uvicorn.run(app, host="0.0.0.0", port=port)
