package com.tekmaturix.tekmaturixvectordbservice.controller;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixvectordbservice.adapter.VectorStoreAdapter;
import com.tekmaturix.tekmaturixvectordbservice.model.VectorStoreRequestObject;

@RestController
@RequestMapping("/api/v1")
public class VectorStoreController {

	private final VectorStoreAdapter vectorStoreAdapter;

	public VectorStoreController(VectorStoreAdapter vectorStoreAdapter) {
		super();
		this.vectorStoreAdapter = vectorStoreAdapter;
	}

	@PostMapping(value = "/common/chat/search", consumes = "application/json", produces = "application/json")
	public List<Document> getProduct(@RequestBody VectorStoreRequestObject vectorStoreRequestObject) {
		return vectorStoreAdapter.getProduct(vectorStoreRequestObject.text());
	}

}
