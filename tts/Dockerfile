# Kai's voice service (server.py). The model and the clip cache live in the /data volume.
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py .

ENV PORT=8090 MODEL_DIR=/data/models CACHE_DIR=/data/cache PYTHONUNBUFFERED=1
EXPOSE 8090
VOLUME /data
HEALTHCHECK --interval=30s --timeout=5s --start-period=5m \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/health', timeout=4)"
CMD ["python", "server.py"]
