package com.tekmaturix.tekmaturixembeddingservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixembeddingservice.adapter.EmbeddingAdapter;

@RestController
@RequestMapping("/api/v1")
public class EMbeddingController {
	
	
	private final EmbeddingAdapter embeddingAdapter;
	
	public EMbeddingController(EmbeddingAdapter embeddingAdapter) {
		super();
		this.embeddingAdapter = embeddingAdapter;
	}
	
	@GetMapping("/common/chat/{text}")
	public float[] embed(@PathVariable String text) {
		return this.embeddingAdapter.embed(text);
	}
	
	@GetMapping("/common/chat/{text1}/{text2}")
	public Double dotProduct(@PathVariable String text1, @PathVariable String text2) {
		return this.embeddingAdapter.dotProduct(text1, text2);
	}

}
