package com.tekmaturix.tekmaturixpgvectordbservice.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class PgVectorService {

	private final VectorStore vectorStore;

	public PgVectorService(VectorStore vectorStore) {
		super();
		this.vectorStore = vectorStore;
	}

	public List<Document> getProducts(String text) {
		try {
			System.out.println("Performing similarity search for query: " + text);
			List<Document> results = vectorStore
					.similaritySearch(SearchRequest.builder().query(text).similarityThreshold(0.5).topK(5).build());
			System.out.println("Found " + results.size() + " documents");
			return results;
		} catch (Exception e) {
			System.err.println("Error during similarity search: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

}
