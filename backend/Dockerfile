FROM python:3.9-slim

RUN useradd -m -u 1000 user
WORKDIR /app

RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY --chown=user . .

RUN mkdir -p uploads && chown -R user:user /app

ENV PORT=7860
ENV HOME=/home/user

USER user

EXPOSE 7860

CMD ["python", "app.py"]
