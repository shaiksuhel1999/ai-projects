from fastapi import APIRouter, File, UploadFile
from request_model import Audio
from voice_creator import AudioCreator
import sys

router = APIRouter(prefix="/api")
creator = AudioCreator()

@router.post("/create-audio")
def generate_audio(req: Audio):
    if req.with_ref:
        output = creator.clone_audio(req)
    else:
        output = creator.create_audio(req)

    return {"status": "success", "output": output}

