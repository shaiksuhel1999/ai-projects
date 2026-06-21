package com.tekmaturix.tekmaturixpgvectordbservice.config;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class OllamaConfig {

	@Bean
	public VectorStore vectorStore(JdbcTemplate jdbcTemplate, OllamaEmbeddingModel embeddingModel) {
		return PgVectorStore.builder(jdbcTemplate, embeddingModel).dimensions(1024)
				.distanceType(PgDistanceType.COSINE_DISTANCE).indexType(PgIndexType.HNSW).build();
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
