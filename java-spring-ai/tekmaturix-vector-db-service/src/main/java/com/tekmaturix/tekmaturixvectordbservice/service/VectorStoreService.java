package com.tekmaturix.tekmaturixvectordbservice.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class VectorStoreService {

	private final VectorStore vectorStore;
	
	// Configuration constants for better search quality
	private static final int TOP_K_RESULTS = 1;
	private static final double SIMILARITY_THRESHOLD = 0.5; // Only return results with score > 50%

	public VectorStoreService(VectorStore vectorStore) {
		super();
		this.vectorStore = vectorStore;
	}

	/**
	 * Search for products using similarity search with optimized parameters
	 * 
	 * @param text - Search query text (e.g., "Samsung Galaxy S24")
	 * @return List of similar products sorted by relevance score (highest first)
	 */
	public List<Document> getProduct(String text) {
		// Perform similarity search with top-k limit
		List<Document> results = vectorStore.similaritySearch(
			SearchRequest.builder()
				.query(text)
				.topK(TOP_K_RESULTS)
				.build()
		);
		
		// Filter by similarity threshold and sort by score (highest first)
		return results.stream()
			.filter(doc -> {
				// Extract similarity score from metadata
				Object scoreObj = doc.getMetadata().get("score");
				if (scoreObj instanceof Number) {
					double score = ((Number) scoreObj).doubleValue();
					return score >= SIMILARITY_THRESHOLD;
				}
				return true; // If no score, include it
			})
			.sorted((doc1, doc2) -> {
				// Sort by score descending (highest relevance first)
				Double score1 = extractScore(doc1);
				Double score2 = extractScore(doc2);
				return score2.compareTo(score1);
			})
			.collect(Collectors.toList());
	}

	/**
	 * Extract similarity score from document metadata
	 */
	private Double extractScore(Document doc) {
		Object scoreObj = doc.getMetadata().get("score");
		if (scoreObj instanceof Number) {
			return ((Number) scoreObj).doubleValue();
		}
		return 0.0;
	}

}
