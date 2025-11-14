package com.example.libreria.service;

import com.example.libreria.dto.ExternalBookDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ExternalBookService {
    
    private final RestTemplate restTemplate;
    
    @Value("${external.api.books.url}")
    private String externalApiUrl;
    
    public ExternalBookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public List<ExternalBookDTO> fetchAllBooks() {
        try {
            log.info("Fetching books from external API: {}", externalApiUrl);
            ResponseEntity<List<ExternalBookDTO>> response = restTemplate.exchange(
                    externalApiUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ExternalBookDTO>>() {}
            );
            
            List<ExternalBookDTO> books = response.getBody();
            log.info("Successfully fetched {} books from external API", books != null ? books.size() : 0);
            return books != null ? books : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Error fetching books from external API: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener libros de la API externa: " + e.getMessage(), e);
        }
    }
    
    public ExternalBookDTO fetchBookById(Long id) {
        try {
            log.info("Fetching book with id {} from external API", id);
            String url = externalApiUrl + "/" + id;
            ExternalBookDTO book = restTemplate.getForObject(url, ExternalBookDTO.class);
            log.info("Successfully fetched book: {}", book != null ? book.getTitle() : "null");
            return book;
        } catch (RestClientException e) {
            log.error("Error fetching book {} from external API: {}", id, e.getMessage(), e);
            throw new RuntimeException("Error al obtener el libro de la API externa: " + e.getMessage(), e);
        }
    }
}

