# reranker/Dockerfile
FROM python:3.10-slim

WORKDIR /app

# системные зависимости (минимум для некоторых колёс)
RUN apt-get update && apt-get install -y gcc && rm -rf /var/lib/apt/lists/*

# зависимости
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir -r requirements.txt \
 && pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.4.1 \
 && pip install --no-cache-dir sentence-transformers==2.7.0

# сервис
COPY reranker_service.py .

# кэши HF внутри образа (чтобы прогрев сохранился)
ENV PYTHONUNBUFFERED=1 \
    HF_HOME=/opt/hf \
    TRANSFORMERS_CACHE=/opt/hf \
    SENTENCE_TRANSFORMERS_HOME=/opt/hf

# ПРОГРЕВ МОДЕЛИ (попадает в слой образа)
RUN python - <<'PY'
from sentence_transformers import CrossEncoder
# замени модель, если нужна другая
CrossEncoder("cross-encoder/ms-marco-MiniLM-L-6-v2")
print("Model cached OK")
PY

EXPOSE 8000
CMD ["uvicorn", "reranker_service:app", "--host", "0.0.0.0", "--port", "8000"]
