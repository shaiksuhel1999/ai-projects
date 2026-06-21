package com.tekmaturix.tekmaturixaudiomodelservice.controller;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tekmaturix.tekmaturixaudiomodelservice.service.OpenAIAudioService;

@RestController
@RequestMapping("/api/v1/")
public class OpenAIAudioController {
	
	
	private final OpenAIAudioService openAIAudioService;
	
	public OpenAIAudioController(OpenAIAudioService openAIAudioService) {
		this.openAIAudioService = openAIAudioService;
	}
	
	public String stt(MultipartFile file) {
		return openAIAudioService.speechToText(file);
	}
	
	@PostMapping("/common/tts")
	public Path tts(String text) throws IOException {
		return openAIAudioService.textToSpeech(text);
	}

}
