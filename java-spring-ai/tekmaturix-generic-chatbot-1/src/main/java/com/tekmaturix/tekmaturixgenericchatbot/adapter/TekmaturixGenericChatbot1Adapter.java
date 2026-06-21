package com.tekmaturix.tekmaturixgenericchatbot.adapter;

import org.springframework.stereotype.Component;

import com.tekmaturix.tekmaturixgenericchatbot.service.TekmaturixGenericChatbot1Service;

@Component
public class TekmaturixGenericChatbot1Adapter {
	
	private final TekmaturixGenericChatbot1Service tekmaturixGenericChatbot1Service;
	
	public TekmaturixGenericChatbot1Adapter(TekmaturixGenericChatbot1Service tekmaturixGenericChatbot1Service) {
		super();
		this.tekmaturixGenericChatbot1Service = tekmaturixGenericChatbot1Service;
	}
	
	public String chat(String prompt) {
		return tekmaturixGenericChatbot1Service.chat(prompt);
	}
	
	public String chatOne(String prompt) {
		return tekmaturixGenericChatbot1Service.chatOne(prompt);
	}

}
