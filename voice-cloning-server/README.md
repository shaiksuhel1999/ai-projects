# 🎙️ Voice Cloning Service using OmniVoice

## 🚀 Prerequisites

- NVIDIA GPU (CUDA supported)
- Docker installed
- Internet access (for downloading models from Hugging Face)

---

## 📦 Setup Instructions

### 1. Clone the project
```text
https://github.com/shaiksuhel1999/ai-projects/tree/main/voice-cloning-server
cd voice-cloning-server
```

### 2. Build Docker Image
```bash
docker build -t voice_enhancer .
```

### 3. Run Docker Container
```bash
docker run --gpus all \
  -v "/local-machine-path:/container-path" \
  -p 8000:8000 \
  -d voice_enhancer
```
```text
Make sure to replace:
/local-machine-path with your local directory (for audio files)
/container-path** with the container directory
```

## Initial Startup
```text
The first run may take some time.
This is due to:
Model download from Hugging Face
OmniVoice initialization on GPU
```

## Health Check
```bash
GET http://localhost:8000/
```

## Generate Audio using Voice Design
```bash
POST http://localhost:8000/api/create-audio
{
  "text": "Hello, Veronica speaking here?",
  "instruction": "female, british accent"
}
```

## Generate Audio using Voice Cloning
```bash
POST http://localhost:8000/api/create-audio
{
  "text": "Hello, Veronica speaking here?",
  "with_ref": true,
  "ref_audio_path": "/container-path/audio_ref.wav",
  "ref_text": "Hello, Veronica speaking here?",
  "target_name": "veronica.wav"
}
```

## Output
```text
Generated audio files will be saved to the mounted volume
```

## Summary
```text
This service supports:

🎙️ Voice Design (via instruction)
🧬 Voice Cloning (via reference audio)
⚡ GPU-accelerated inference using OmniVoice
```


