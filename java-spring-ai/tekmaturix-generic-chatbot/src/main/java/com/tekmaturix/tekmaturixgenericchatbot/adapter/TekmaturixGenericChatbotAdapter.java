package com.tekmaturix.tekmaturixgenericchatbot.adapter;

import org.springframework.stereotype.Component;

import com.tekmaturix.tekmaturixgenericchatbot.service.TekmaturixGenericChatbotService;

@Component
public class TekmaturixGenericChatbotAdapter {
	
	private final TekmaturixGenericChatbotService tekmaturixGenericChatbotService;
	
	public TekmaturixGenericChatbotAdapter(TekmaturixGenericChatbotService tekmaturixGenericChatbotService) {
		this.tekmaturixGenericChatbotService = tekmaturixGenericChatbotService;
	}
	
	public String chat(String prompt) {
		return tekmaturixGenericChatbotService.chat(prompt);
	}

}
