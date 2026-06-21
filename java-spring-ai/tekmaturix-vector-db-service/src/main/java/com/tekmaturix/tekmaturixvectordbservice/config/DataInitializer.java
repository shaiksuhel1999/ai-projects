package com.tekmaturix.tekmaturixvectordbservice.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Configuration
public class DataInitializer {

	private final VectorStore vectorStore;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public DataInitializer(VectorStore vectorStore) {
		super();
		this.vectorStore = vectorStore;
	}

	@PostConstruct
	public void init() {
		try {
			String jsonContent = new String(new ClassPathResource("product-details.txt").getContentAsByteArray());
			JsonNode rootNode = objectMapper.readTree(jsonContent);
			JsonNode productsArray = rootNode.get("products");

			List<Document> documents = new ArrayList<>();

			if (productsArray.isArray()) {
				for (JsonNode productNode : productsArray) {
					Document doc = createDocumentFromProduct(productNode);
					documents.add(doc);
				}
			}

			if (!documents.isEmpty()) {
				this.vectorStore.add(documents);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to load products from JSON file", e);
		}
	}

	private Document createDocumentFromProduct(JsonNode productNode) {
		String productId = productNode.get("productId").asText();
		String name = productNode.get("name").asText();
		String brand = productNode.get("brand").asText();
		String category = productNode.get("category").asText();
		String price = productNode.get("price").asText();

		StringBuilder contentBuilder = new StringBuilder();
		// Build comprehensive content for better semantic search
		// Repeated key terms help embedding model understand importance
		contentBuilder.append(name).append(". ");
		contentBuilder.append(brand).append(" ").append(name).append(". ");
		contentBuilder.append("Product Name: ").append(name).append(". ");
		contentBuilder.append("Brand: ").append(brand).append(". ");
		contentBuilder.append("Category: ").append(category).append(". ");
		contentBuilder.append("Price: ").append(price).append(". ");

		if (productNode.has("features") && productNode.get("features").isArray()) {
			List<String> features = new ArrayList<>();
			for (JsonNode feature : productNode.get("features")) {
				features.add(feature.asText());
			}
			contentBuilder.append("Features: ").append(String.join(", ", features)).append(". ");
		}

		// Create metadata map for search filtering and response enrichment
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("productId", productId);
		metadata.put("name", name);
		metadata.put("brand", brand);
		metadata.put("category", category);
		metadata.put("price", price);

		return new Document(contentBuilder.toString(), metadata);
	}

}
