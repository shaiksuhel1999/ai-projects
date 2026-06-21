package com.tekmaturix.tekmaturixvectordbservice.controller;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixvectordbservice.model.RedisSearchRequest;
import com.tekmaturix.tekmaturixvectordbservice.service.RedisVectorStoreService;

@RestController
@RequestMapping("/api/v1")
public class RedisVectorStoreController {
	
	private final RedisVectorStoreService redisVectorStoreService;
	
	public RedisVectorStoreController(RedisVectorStoreService redisVectorStoreService) {
		this.redisVectorStoreService = redisVectorStoreService;
	}
	
	@GetMapping("/common/search")
	public List<Document> search() {
		return redisVectorStoreService.search();
	}
	
	@PostMapping("/common/search-product")
	public List<Document> searchProduct(@RequestBody RedisSearchRequest request) {
		return redisVectorStoreService.search(request.query());
	}
	
	@PostMapping("/common/search-with-rag")
	public String searchWithRAG(@RequestBody RedisSearchRequest request) {
		return redisVectorStoreService.getAnswerUsingRag(request.query());
	}

}
