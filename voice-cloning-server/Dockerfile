FROM nvidia/cuda:12.1.1-cudnn8-runtime-ubuntu22.04

# install system deps
RUN apt-get update && apt-get install -y \
    python3 python3-pip git ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# set python alias
RUN ln -s /usr/bin/python3 /usr/bin/python

# upgrade pip
RUN pip install --upgrade pip

# install python deps
# install pytorch (from pytorch index)
RUN pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121

# install other python deps (from default PyPI)
RUN pip install fastapi uvicorn omnivoice

# set working dir FIRST
WORKDIR /app

# copy code
COPY ./app /app

# run server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]