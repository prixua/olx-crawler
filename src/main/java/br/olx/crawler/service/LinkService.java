package br.olx.crawler.service;

import br.olx.crawler.entity.Link;
import br.olx.crawler.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class LinkService {
    private final LinkRepository linkRepository;

    public Mono<Link> registerLink(String uri) {
        return linkRepository.findByUri(uri)
            .flatMap(existing -> {
                if (nonNull(existing)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "URL já cadastrada"));
                }
                return createNewLink(uri);
            })
            .switchIfEmpty(Mono.defer(() ->createNewLink(uri)));
    }

    private Mono<Link> createNewLink(String uri) {
        return linkRepository.save(Link.builder().enabled(true).uri(uri).build());
    }
}
