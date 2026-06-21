package com.tekmaturix.tekmaturixvectordbservice.config;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

	@Bean
	public VectorStore vectorStore(OllamaEmbeddingModel ollamaEmbeddingModel) {
		return SimpleVectorStore.builder(ollamaEmbeddingModel).build();
	}

}
