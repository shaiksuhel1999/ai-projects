package com.tekmaturix.tekmaturixembeddingservice.service;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {
	
	
	private final EmbeddingModel embeddingModel;
	
	public EmbeddingService(OllamaEmbeddingModel embeddingModel) {
		super();
		this.embeddingModel = embeddingModel;
	}
	
	
	public float[] getEmbedding(String text) {
		return this.embeddingModel.embed(text);
	}
	
	
	public Double dotProduct(String text1, String text2) {
		return calculateCosineSimilarity(embeddingModel.embed(text1), embeddingModel.embed(text2));
	}
	
	 private double calculateCosineSimilarity(float[] v1, float[] v2) {

	        if (v1.length != v2.length) {
	            throw new IllegalArgumentException("Vectors must have same dimensions");
	        }

	        double dotProduct = 0.0;
	        double normA = 0.0;
	        double normB = 0.0;

	        for (int i = 0; i < v1.length; i++) {
	            dotProduct += v1[i] * v2[i];
	            normA += Math.pow(v1[i], 2);
	            normB += Math.pow(v2[i], 2);
	        }

	        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	    }

}
