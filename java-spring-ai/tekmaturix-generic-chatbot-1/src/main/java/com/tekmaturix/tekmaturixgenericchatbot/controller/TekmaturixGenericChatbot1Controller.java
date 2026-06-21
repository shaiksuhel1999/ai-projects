package com.tekmaturix.tekmaturixgenericchatbot.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixgenericchatbot.adapter.TekmaturixGenericChatbot1Adapter;
import com.tekmaturix.tekmaturixgenericchatbot.model.OllamaRequestObject;
import com.tekmaturix.tekmaturixgenericchatbot.model.OllamaResponseObject;

@RestController
@RequestMapping("/api/v1")
public class TekmaturixGenericChatbot1Controller {
	
	private final TekmaturixGenericChatbot1Adapter tekmaturixGenericChatbot1Adapter;
	
	public TekmaturixGenericChatbot1Controller(TekmaturixGenericChatbot1Adapter tekmaturixGenericChatbot1Adapter) {
		super();
		this.tekmaturixGenericChatbot1Adapter = tekmaturixGenericChatbot1Adapter;
	}
	
	@PostMapping(value = "/common/chat", consumes = "application/json", produces = "application/json")
	public OllamaResponseObject chat(@RequestBody OllamaRequestObject ollamaRequestObject) {
		return new OllamaResponseObject(tekmaturixGenericChatbot1Adapter.chat(ollamaRequestObject.prompt()));
	}

	@PostMapping(value = "/common/chat-one", consumes = "application/json", produces = "application/json")
	public OllamaResponseObject chatOne(@RequestBody OllamaRequestObject ollamaRequestObject) {
		return new OllamaResponseObject(tekmaturixGenericChatbot1Adapter.chatOne(ollamaRequestObject.prompt()));
	}
}
