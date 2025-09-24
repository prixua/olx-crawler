package br.olx.crawler.repository;

import br.olx.crawler.entity.Link;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinkRepository extends ReactiveMongoRepository<Link, String> {
    Mono<Link> findByUri(String uri);
    Flux<Link> findByEnabledTrue();
}
