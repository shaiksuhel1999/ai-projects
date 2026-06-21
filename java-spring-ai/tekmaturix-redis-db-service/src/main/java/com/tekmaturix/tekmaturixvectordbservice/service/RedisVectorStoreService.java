package com.tekmaturix.tekmaturixvectordbservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.tekmaturix.tekmaturixvectordbservice.model.Product;
import com.tekmaturix.tekmaturixvectordbservice.model.ProductWrapper;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.ObjectMapper;

@Service
public class RedisVectorStoreService {

	@Autowired
	VectorStore vectorStore;

	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	ChatClient ollamaChatClient;

	@PostConstruct
	public void init() {
		loadProducts();
	}

	public void loadProducts() {

		try {

			ClassPathResource resource = new ClassPathResource("product-details.txt");

			ProductWrapper wrapper = objectMapper.readValue(resource.getInputStream(), ProductWrapper.class);

			List<Document> documents = wrapper.getProducts().stream().map(this::convertToDocument).toList();

			vectorStore.add(documents);

			System.out.println("Loaded {} products into Redis Vector Store" + documents.size());

		} catch (Exception e) {
			System.err.println("Error loading products" + e);
		}
	}

	private Document convertToDocument(Product product) {

		String content = """
				Product ID: %s
				Product Name: %s
				Brand: %s
				Category: %s
				Price: %s
				Features: %s
				Description: This is a product with the following details: %s from brand %s in the %s category priced at %s
				""".formatted(product.getProductId(), product.getName(), product.getBrand(), product.getCategory(), 
				product.getPrice(), String.join(", ", product.getFeatures()),
				product.getName(), product.getBrand(), product.getCategory(), product.getPrice());

		Map<String, Object> metadata = new HashMap<>();

		metadata.put("productId", product.getProductId());
		metadata.put("name", product.getName());
		metadata.put("brand", product.getBrand());
		metadata.put("category", product.getCategory());
		metadata.put("price", product.getPrice());

		return new Document(content, metadata);
	}

	public List<Document> search() {

		List<Document> documents = List.of(
				new Document(
						"Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
						Map.of("meta1", "meta1")),
				new Document("The World is Big and Salvation Lurks Around the Corner"),
				new Document("You walk forward facing the past and you turn back toward the future.",
						Map.of("meta2", "meta2")));

		vectorStore.add(documents);

		List<Document> results = this.vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

		return results;
	}

	public List<Document> search(String query) {
		List<Document> results = this.vectorStore
				.similaritySearch(SearchRequest.builder()
					.query(query)
					.topK(5)
					.similarityThreshold(0.0)
					.build());
		return results;
	}
	
	public List<Document> getAllProducts() {
		// Retrieve all products using a generic search term
		List<Document> results = this.vectorStore
				.similaritySearch(SearchRequest.builder()
					.query("product")
					.topK(100)
					.similarityThreshold(0.0)
					.build());
		return results;
	}
	
	public String getAnswerUsingRag(String query) {
		// Check if this is a product list query
		boolean isListQuery = query.toLowerCase().contains("all product") || 
							  query.toLowerCase().contains("list") || 
							  query.toLowerCase().contains("show all") ||
							  query.toLowerCase().contains("available product");
		
		return ollamaChatClient
				.prompt()
				.system("You are a helpful product assistant. Answer the user's question based on the provided product information context. When listing products, provide all the products mentioned in the context with their complete details including ID, name, price, brand, category, and features. If the answer is not found in the context, politely say you don't have that information.")
				.user(query)
				.advisors(QuestionAnswerAdvisor.builder(vectorStore)
					.build())
				.call()
				.content();
	}
}