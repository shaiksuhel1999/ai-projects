package com.tekmaturix.tekmaturixembeddingservice.adapter;

import org.springframework.stereotype.Component;

import com.tekmaturix.tekmaturixembeddingservice.service.EmbeddingService;

@Component
public class EmbeddingAdapter {
	
	
	private final EmbeddingService embeddingService;
	
	public EmbeddingAdapter(EmbeddingService embeddingService) {
		super();
		this.embeddingService = embeddingService;
	}
	
	public float[] embed(String text) {
		return this.embeddingService.getEmbedding(text);
	}
	
	
	public Double dotProduct(String text1, String text2) {
		return this.embeddingService.dotProduct(text1, text2);
	}

}
