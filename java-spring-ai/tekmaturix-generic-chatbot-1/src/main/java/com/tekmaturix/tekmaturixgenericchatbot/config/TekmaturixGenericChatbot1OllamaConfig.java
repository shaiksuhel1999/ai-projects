package com.tekmaturix.tekmaturixgenericchatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TekmaturixGenericChatbot1OllamaConfig {

	@Bean
	public ChatClient ollamaChatClient(ChatClient.Builder builder) {

		ChatMemory memory = MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository())
				.maxMessages(1000).build();
		return builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build()).build();
	}

}
