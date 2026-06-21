package com.tekmaturix.tekmaturixvectordbservice.adapter;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.tekmaturix.tekmaturixvectordbservice.service.VectorStoreService;

@Component
public class VectorStoreAdapter {

	private final VectorStoreService vectorStoreService;

	public VectorStoreAdapter(VectorStoreService vectorStoreService) {
		super();
		this.vectorStoreService = vectorStoreService;
	}

	public List<Document> getProduct(String text) {
		return vectorStoreService.getProduct(text);
	}

}
