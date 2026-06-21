package com.tekmaturix.tekmaturixpgvectordbservice.controller;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tekmaturix.tekmaturixpgvectordbservice.adapter.PgVectorAdapter;
import com.tekmaturix.tekmaturixpgvectordbservice.model.PgVectorRequestObject;

@RestController
@RequestMapping("/api/v1")
public class PgVectorController {
	
	
	private final PgVectorAdapter pgVectorAdapter;
	
	public PgVectorController(PgVectorAdapter pgVectorAdapter) {
		super();
		this.pgVectorAdapter = pgVectorAdapter;
	}
	
	
	
	@PostMapping(value = "/common/chat/search", consumes = "application/json", produces = "application/json")
	public List<Document> getProducts(@RequestBody PgVectorRequestObject pgVectorRequestObject) {
		return this.pgVectorAdapter.getProducts(pgVectorRequestObject.text());
	}
	
	
	@GetMapping(value = "/user/test", consumes = "application/json", produces = "application/json")
	public String userTest() {
		return "{\"hello\"}";
	}
	
	
	@GetMapping(value = "/admin/test", consumes = "application/json", produces = "application/json")
	public String adminTest() {
		return "{\"hello\"}";
	}
	
	@GetMapping(value = "/test")
	public String test() {
		return "test";
	}
	

}
