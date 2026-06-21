package com.tekmaturix.tekmaturixgenericchatbot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class TekmaturixGenericChatbotService {

	
	private final ChatClient ollamaChatClient;
	
	public TekmaturixGenericChatbotService(ChatClient ollamaChatClient) {
		this.ollamaChatClient = ollamaChatClient;
	}
	
	public String chat(String prompt) {
		return ollamaChatClient.prompt(prompt)
				.call()
				.content();
	}
}
