package com.tekmaturix.tekmaturixpgvectordbservice.adapter;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.tekmaturix.tekmaturixpgvectordbservice.service.PgVectorService;

@Component
public class PgVectorAdapter {

	private final PgVectorService pgVectorService;

	public PgVectorAdapter(PgVectorService pgVectorService) {
		super();
		this.pgVectorService = pgVectorService;
	}

	public List<Document> getProducts(String text) {
		return this.pgVectorService.getProducts(text);
	}

}
