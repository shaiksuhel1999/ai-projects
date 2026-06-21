package com.tekmaturix.tekmaturixgenericchatbot.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixgenericchatbot.adapter.TekmaturixGenericChatbotAdapter;
import com.tekmaturix.tekmaturixgenericchatbot.model.OllamaRequestObject;
import com.tekmaturix.tekmaturixgenericchatbot.model.OllamaResponseObject;

@RestController
@RequestMapping("/api/v1")
public class TekmaturixGenericChatbotController {
	
	private final TekmaturixGenericChatbotAdapter tekmaturixGenericChatbotAdapter;
	
	public TekmaturixGenericChatbotController(TekmaturixGenericChatbotAdapter tekmaturixGenericChatbotAdapter) {
		this.tekmaturixGenericChatbotAdapter = tekmaturixGenericChatbotAdapter;
	}
	
	
	@PostMapping(value = "/common/chat")
	public OllamaResponseObject chat(@RequestBody OllamaRequestObject requestObject) {
		return new OllamaResponseObject(tekmaturixGenericChatbotAdapter.chat(requestObject.getPrompt()));
	}

}
