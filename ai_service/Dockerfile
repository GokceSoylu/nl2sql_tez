FROM python:3.11-slim

WORKDIR /app

# Sistem bağımlılıkları
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Ana dizinden requirements.txt kopyalama (eğer ana dizindeyse)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Tüm proje kodunu kopyala
COPY . .

# Python yoluna kök dizini ekle (agent, database ve ai_service modüllerini bulabilmesi için)
ENV PYTHONPATH=/app

EXPOSE 8000

# app/main.py dosyasındaki FastAPI uygulamasını başlatır
CMD ["uvicorn", "ai_service.app.main:app", "--host", "0.0.0.0", "--port", "8000"]