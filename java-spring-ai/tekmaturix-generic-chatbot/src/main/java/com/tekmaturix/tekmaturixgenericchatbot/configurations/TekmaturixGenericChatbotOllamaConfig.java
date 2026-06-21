package com.tekmaturix.tekmaturixgenericchatbot.configurations;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TekmaturixGenericChatbotOllamaConfig {
	
	@Bean
	public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
		return ChatClient.create(ollamaChatModel);
	}

}
