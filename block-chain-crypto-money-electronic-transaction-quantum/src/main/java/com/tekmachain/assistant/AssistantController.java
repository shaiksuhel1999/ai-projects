package com.tekmachain.assistant;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    public record Ask(String question) {
    }

    @PostMapping
    public AssistantService.AnswerDto ask(@RequestBody Ask req) {
        return assistant.answer(req.question());
    }

    @GetMapping("/examples")
    public List<String> examples() {
        return assistant.examples();
    }
}
