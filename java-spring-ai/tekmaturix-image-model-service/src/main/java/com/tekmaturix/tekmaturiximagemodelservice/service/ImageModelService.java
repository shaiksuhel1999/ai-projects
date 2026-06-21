package com.tekmaturix.tekmaturiximagemodelservice.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ImageModelService {
	
	private static final String DOWNLOADS_PATH = "C:\\Users\\SHAIK SUHELBASHA\\Downloads";
	
	private final OpenAiImageModel openAiImageModel;
	
	private final ChatClient chatClient;
	
	public ImageModelService(OpenAiImageModel openAiImageModel, ChatClient.Builder chatClientBuilder) {
		this.openAiImageModel = openAiImageModel;
		this.chatClient = chatClientBuilder.build();
	}
	
	
	public String generateImage(String prompt) {
		ImagePrompt imagePrompt = new ImagePrompt(prompt);
		
		ImageResponse response = openAiImageModel.call(imagePrompt);
		
		System.out.println("Received response from OpenAI Image Model: " + response);
		System.out.println("Response Results: " + response.getResults());
		
		if (response.getResults() == null || response.getResults().isEmpty()) {
			System.err.println("No results in response");
			return null;
		}
		
		// Get the first result
		String imageUrl = response.getResult().getOutput().getUrl();
		System.out.println("Image URL: " + imageUrl);
		
		if (imageUrl == null || imageUrl.isEmpty()) {
			System.err.println("Image URL is null or empty");
			// Try alternative way to get the URL
			System.out.println("Response Result Output: " + response.getResult().getOutput());
			return null;
		}
		
		try {
			return downloadAndSaveImage(imageUrl, prompt);
		} catch (IOException e) {
			System.err.println("Error saving image: " + e.getMessage());
			e.printStackTrace();
			return imageUrl; // Return URL as fallback if saving fails
		}
	}
	
	private String downloadAndSaveImage(String imageUrl, String prompt) throws IOException {
		if (imageUrl == null || imageUrl.isEmpty()) {
			throw new IOException("Image URL is null or empty");
		}
		
		// Create downloads directory if it doesn't exist
		Path downloadsDir = Paths.get(DOWNLOADS_PATH);
		Files.createDirectories(downloadsDir);
		
		// Generate filename with timestamp
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		String filename = "generated_image_" + timestamp + ".png";
		
		Path filePath = downloadsDir.resolve(filename);
		
		// Download and save the image
		URL url = new URL(imageUrl);
		try (InputStream inputStream = url.openStream()) {
			Files.copy(inputStream, filePath);
		}
		
		System.out.println("Image saved to: " + filePath.toAbsolutePath());
		
		return filePath.toAbsolutePath().toString();
	}
	
	public String describeImage(String prompt, MultipartFile file) {
		
		return chatClient.prompt()
				.user(us->us.text(prompt).media(MimeType.valueOf("image/jpeg"), file.getResource()))
				.call()
				.content();
		
	}
	

}
