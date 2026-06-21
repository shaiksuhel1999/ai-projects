package com.tekmaturix.tekmaturixgenericchatbot.model;

public class OllamaRequestObject {
	
	private String prompt;
	
	public OllamaRequestObject() {
		super();
	}
	
	public OllamaRequestObject(String prompt) {
		super();
		this.prompt = prompt;
	}
	
	
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
	
	public String getPrompt() {
		return this.prompt;
	}

}
