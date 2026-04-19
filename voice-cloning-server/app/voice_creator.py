from omnivoice import OmniVoice
import torch
import soundfile as sf
import torchaudio

from handlers import AppException
from request_model import Audio
import sys

class AudioCreator:
    def __init__(self):
        self.model = OmniVoice.from_pretrained(
            "k2-fsa/OmniVoice",
            device_map="cuda:0",
            dtype=torch.float32
        )

    def create_audio(self, req: Audio):
        try:
            if not req.text:
                raise AppException("Text is Required to create audio")
            if not req.instruction:
                raise AppException("Instruction is Required to create audio")
            print(f"Started generating the audio with name generated_audio.wav", file=sys.stdout, flush=True)
            audio = self.model.generate(
                text=req.text,
                instruction=req.instruction,
                top_p=req.top_p,
                temperature=req.temperature,
                language_id=req.language_id,
                num_steps=req.num_steps,
                speed=req.speed
            )
            sf.write(f"{req.target_location}/{req.target_name}", audio[0], 24000)
            print(f"generated audio with name generated_audio.wav", file=sys.stdout, flush=True)
        except AppException as e:
            raise e
        except Exception as ex:
            raise ex

    def clone_audio(self, req: Audio):
        try:
            if not req.text:
                raise AppException("Text is Required to clone audio")
            if not req.ref_audio_path:
                raise AppException("Ref audio is required to clone audio")
            print(f"Started cloning audio with name cloned_audio.wav", file=sys.stdout, flush=True)
            audio = self.model.generate(
                text=req.text,
                ref_audio=req.ref_audio_path,
                language_id=req.language_id,
                num_steps=req.num_steps,
                speed=req.speed,
                temperature=req.temperature,
                top_p=req.top_p,
                top_k=1
            )
            sf.write(f"{req.target_location}/{req.target_name}", audio[0], 24000)
            print(f"cloned audio with name cloned_audio.wav", file=sys.stdout, flush=True)
        except AppException as e:
            raise e
        except Exception as ex:
            raise ex
