package com.tekmaturix.tekmaturixstructuredoutputconverter.controller;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StructureOutPutController {
	
	private final ChatClient chatClient;
	
	
	public StructureOutPutController(OllamaChatModel ollamaChatModel) {
		this.chatClient = ChatClient.create(ollamaChatModel);
	}
	
	
	@GetMapping("/convert")
	public List<String> getMovies(@RequestParam String name) {

	    String message = """
	            list top 5 movies of {name}
	            {format}
	            """;

	    ListOutputConverter outputConverter =
	            new ListOutputConverter(new DefaultConversionService());

	    PromptTemplate promptTemplate = new PromptTemplate(message);

	    Prompt prompt = promptTemplate.create(
	        Map.of(
	            "name", name,
	            "format", outputConverter.getFormat()
	        )
	    );

	    return outputConverter.convert(
	            chatClient.prompt(prompt)
	                    .call()
	                    .content()
	    );
	}

}
