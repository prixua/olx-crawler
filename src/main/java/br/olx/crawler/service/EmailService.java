package br.olx.crawler.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${crawler.email.to}")
    private String to;

    @Value("${crawler.email.from}")
    private String from;

    public Mono<Void> sendSummaryEmail(String corpoHtml) {
        return Mono.fromRunnable(() -> {
            String subject = "Resumo dos produtos dos links monitorados";
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(to);
                helper.setFrom(from);
                helper.setSubject(subject);
                helper.setText(corpoHtml, true);
                mailSender.send(message);
                log.info("E-mail resumo enviado para {}", to);
            } catch (Exception e) {
                log.error("Erro ao enviar e-mail HTML", e);
                throw new RuntimeException(e);
            }
        });
    }
}
