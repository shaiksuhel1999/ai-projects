package com.tekmaturix.tekmaturixbeanoutputconverter.controller;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixbeanoutputconverter.model.Movie;

@RestController
@RequestMapping("/api/v1")
public class MoviesController {

	private final ChatClient chatClient;

	public MoviesController(OllamaChatModel ollamaChatModel) {
		super();
		this.chatClient = ChatClient.create(ollamaChatModel);
	}

	@GetMapping("/movies")
	public Movie getMovieData(String name) {

		String message = """
				Get me the best movie of {name}
				{format}
				""";

		BeanOutputConverter<Movie> converter = new BeanOutputConverter<>(Movie.class);
		PromptTemplate template = new PromptTemplate(message);
		Prompt prompt = template.create(Map.of("name", name, "format", converter.getFormat()));
		return converter.convert(chatClient.prompt(prompt).call().content());

	}

}
