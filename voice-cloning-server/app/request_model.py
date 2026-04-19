from pydantic import BaseModel
from typing import Optional

class Audio(BaseModel):
    text: str
    target_location: str = "/var/audio"
    target_name: str = "audio_file.wav"
    instruction: Optional[str] = None
    with_ref: bool = False
    ref_audio_path: Optional[str] = None
    num_steps: Optional[int] = 100
    language_id: str = "en"
    top_p: float = 0.9
    temperature: float = 0.7
    speed: float = 1.0
