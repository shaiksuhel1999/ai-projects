package com.tekmaturix.tekmaturiximagemodelservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tekmaturix.tekmaturiximagemodelservice.model.ImageRequestModel;
import com.tekmaturix.tekmaturiximagemodelservice.service.ImageModelService;

@RestController
@RequestMapping("/api/v1")
public class ImageModelController {
	
	private final ImageModelService imageModelService;
	
	public ImageModelController(ImageModelService imageModelService) {
		this.imageModelService = imageModelService;
	}
	
	
	@PostMapping("/common/generate-image")
	public String generateImage(@RequestBody ImageRequestModel request) {
		System.out.println("Received request to generate image with prompt: " + request.prompt());
		return imageModelService.generateImage(request.prompt());
	}
	
	@PostMapping("/common/describe-image")
	public String describeImage(@RequestParam String prompt, @RequestParam MultipartFile file) {
		return imageModelService.describeImage(prompt, file);
	}

}
