package com.tekmaturix.tekmaturixpgvectordbservice.dao;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProductDataLoader implements CommandLineRunner {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final VectorStore vectorStore;

	public ProductDataLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, VectorStore vectorStore) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.vectorStore = vectorStore;
	}

	@Override
	public void run(String... args) throws Exception {
		ClassPathResource classPathResource = new ClassPathResource("product-details.txt");
		ProductWrapper productWrapper = objectMapper.readValue(classPathResource.getInputStream(),
				ProductWrapper.class);

		// Load products into the products table
		productWrapper.getProducts().forEach(product -> {
			jdbcTemplate.update("""
					    INSERT INTO products
					    (product_id, name, brand, category, price, features)
					    VALUES (?, ?, ?, ?, ?, ?)
					    ON CONFLICT (product_id) DO NOTHING
					""", product.getProductId(), product.getName(), product.getBrand(), product.getCategory(),
					product.getPrice(), String.join(", ", product.getFeatures()));
		});

		System.out.println("Products Loaded Successfully");

		// Load products into vector_store for similarity search
		List<Document> documents = productWrapper.getProducts().stream()
				.map(product -> {
					String content = String.format("Product: %s\nBrand: %s\nCategory: %s\nPrice: %s\nFeatures: %s",
							product.getName(), product.getBrand(), product.getCategory(), product.getPrice(),
							String.join(", ", product.getFeatures()));
					return new Document(content);
				})
				.toList();

		// Add documents to vector store (embeddings will be generated automatically)
		if (!documents.isEmpty()) {
			vectorStore.add(documents);
			System.out.println("Documents added to vector store for similarity search");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ProductWrapper {
		private List<Product> products;

		public List<Product> getProducts() {
			return products;
		}

		public void setProducts(List<Product> products) {
			this.products = products;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Product {

		private Integer productId;
		private String name;
		private String brand;
		private String category;
		private String price;
		private List<String> features;

		public Integer getProductId() {
			return productId;
		}

		public void setProductId(Integer productId) {
			this.productId = productId;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getBrand() {
			return brand;
		}

		public void setBrand(String brand) {
			this.brand = brand;
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}

		public String getPrice() {
			return price;
		}

		public void setPrice(String price) {
			this.price = price;
		}

		public List<String> getFeatures() {
			return features;
		}

		public void setFeatures(List<String> features) {
			this.features = features;
		}

	}

}
