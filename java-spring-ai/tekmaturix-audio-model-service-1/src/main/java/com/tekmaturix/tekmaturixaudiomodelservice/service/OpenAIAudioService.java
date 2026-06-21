package com.tekmaturix.tekmaturixaudiomodelservice.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OpenAIAudioService {
	
	
	private final OpenAiAudioTranscriptionModel aiAudioTranscriptionModel;
	
	private final OpenAiAudioSpeechModel aiAudioSpeechModel;
	
	public OpenAIAudioService(OpenAiAudioTranscriptionModel aiAudioTranscriptionModel, OpenAiAudioSpeechModel aiAudioSpeechModel) {
		this.aiAudioTranscriptionModel = aiAudioTranscriptionModel;
		this.aiAudioSpeechModel = aiAudioSpeechModel;
	}
	
	public String speechToText(MultipartFile file) {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder().build();
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(file.getResource(), options);
		return aiAudioTranscriptionModel.call(prompt).getResult().getOutput();
	}
	
	
	public Path textToSpeech(String text) throws IOException {

	    OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
	            .speed(1.5)
	            .voice(OpenAiAudioSpeechOptions.Voice.NOVA)
	            .build();

	    TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);

	    byte[] audioBytes = aiAudioSpeechModel.call(prompt)
	            .getResult()
	            .getOutput();

	    Path outputPath = Paths.get("D:/audio/output.mp3");

	    Files.createDirectories(outputPath.getParent());
	    Files.write(outputPath, audioBytes);

	    return outputPath;
	}
	

}
