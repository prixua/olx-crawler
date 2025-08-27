package br.olx.crawler;

import br.olx.crawler.service.AuthService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;

@SpringBootApplication
@RequiredArgsConstructor
public class OlxCrawlerApplication {

    private final AuthService authService;

    public static void main(String[] args) {
        SpringApplication.run(OlxCrawlerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDefaultUser() {
        authService.initializeDefaultUser().subscribe();
    }
}
