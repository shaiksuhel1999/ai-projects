package com.tekmaturix.tekmaturixgenericchatbot.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Service
public class TekmaturixGenericChatbot1Service {

	private final ChatClient ollamaChatClient;

	public TekmaturixGenericChatbot1Service(ChatClient ollamaChatClient) {
		super();
		this.ollamaChatClient = ollamaChatClient;
	}

	public String chat(String prompt) {
		PromptTemplate promptTemplate = new PromptTemplate(prompt);
		Prompt promptObject = promptTemplate.create();
		return ollamaChatClient.prompt(promptObject).call().content();
	}
	
	public String chatOne(String prompt) {
		Prompt promptObject = new PromptTemplate(prompt)
				.create(Map.of("letter", "S"));
		System.out.println(promptObject);
		return ollamaChatClient.prompt(promptObject).call().content();
	}

}
