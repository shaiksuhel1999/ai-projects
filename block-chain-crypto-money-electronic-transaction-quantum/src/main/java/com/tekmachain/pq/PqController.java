package com.tekmachain.pq;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pq")
public class PqController {

    private final PostQuantumService pq;

    public PqController(PostQuantumService pq) {
        this.pq = pq;
    }

    @GetMapping("/benchmark")
    public PostQuantumService.PqReport benchmark() {
        return pq.benchmark();
    }
}
