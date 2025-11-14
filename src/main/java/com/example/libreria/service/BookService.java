package com.example.libreria.service;

import com.example.libreria.dto.BookResponseDTO;
import com.example.libreria.dto.ExternalBookDTO;
import com.example.libreria.model.Book;
import com.example.libreria.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookService {
    
    private final BookRepository bookRepository;
    private final ExternalBookService externalBookService;
    
    @Transactional
    public void syncBooksFromExternalApi() {
        log.info("Synchronizing books from external API");
        List<ExternalBookDTO> externalBooks = externalBookService.fetchAllBooks();
        
        for (ExternalBookDTO externalBook : externalBooks) {
            Book existingBook = bookRepository.findByExternalId(externalBook.getId())
                    .orElse(null);
            
            if (existingBook == null) {
                Book newBook = convertToBook(externalBook);
                newBook.setStockQuantity(10); // Stock inicial por defecto
                newBook.setAvailableQuantity(10);
                bookRepository.save(newBook);
                log.info("Created new book: {}", newBook.getTitle());
            } else {
                // Actualizar información del libro
                updateBookFromExternal(existingBook, externalBook);
                bookRepository.save(existingBook);
                log.info("Updated book: {}", existingBook.getTitle());
            }
        }
        log.info("Synchronization completed");
    }
    
    @Transactional(readOnly = true)
    public List<BookResponseDTO> getAllBooks() {
        return bookRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public BookResponseDTO getBookByExternalId(Long externalId) {
        Book book = bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
        return convertToDTO(book);
    }
    
    @Transactional
    public BookResponseDTO updateStock(Long externalId, Integer stockQuantity) {
        Book book = bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
        
        int reserved = book.getStockQuantity() - book.getAvailableQuantity();
        if (stockQuantity < reserved) {
            throw new RuntimeException("No se puede reducir el stock por debajo de los libros reservados: " + reserved);
        }
        
        book.setStockQuantity(stockQuantity);
        book.setAvailableQuantity(stockQuantity - reserved);
        bookRepository.save(book);
        
        return convertToDTO(book);
    }
    
    @Transactional
    public void decreaseAvailableQuantity(Long externalId) {
        Book book = bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
        
        if (book.getAvailableQuantity() <= 0) {
            throw new RuntimeException("No hay libros disponibles para reservar");
        }
        
        book.setAvailableQuantity(book.getAvailableQuantity() - 1);
        bookRepository.save(book);
    }
    
    @Transactional
    public void increaseAvailableQuantity(Long externalId) {
        Book book = bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
        
        if (book.getAvailableQuantity() >= book.getStockQuantity()) {
            throw new RuntimeException("La cantidad disponible no puede exceder el stock");
        }
        
        book.setAvailableQuantity(book.getAvailableQuantity() + 1);
        bookRepository.save(book);
    }
    
    @Transactional(readOnly = true)
    public Book getBookEntityByExternalId(Long externalId) {
        return bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
    }
    
    @Transactional(readOnly = true)
    public boolean isBookAvailable(Long externalId) {
        Book book = bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
        return book.getAvailableQuantity() > 0;
    }
    
    private Book convertToBook(ExternalBookDTO dto) {
        Book book = new Book();
        book.setExternalId(dto.getId());
        book.setTitle(dto.getTitle());
        book.setAuthorName(dto.getAuthorName());
        book.setFirstPublishYear(dto.getFirstPublishYear());
        book.setEditionCount(dto.getEditionCount());
        book.setHasFulltext(dto.getHasFulltext());
        book.setPrice(dto.getPrice());
        return book;
    }
    
    private void updateBookFromExternal(Book book, ExternalBookDTO dto) {
        book.setTitle(dto.getTitle());
        book.setAuthorName(dto.getAuthorName());
        book.setFirstPublishYear(dto.getFirstPublishYear());
        book.setEditionCount(dto.getEditionCount());
        book.setHasFulltext(dto.getHasFulltext());
        book.setPrice(dto.getPrice());
    }
    
    private BookResponseDTO convertToDTO(Book book) {
        BookResponseDTO dto = new BookResponseDTO();
        dto.setExternalId(book.getExternalId());
        dto.setTitle(book.getTitle());
        dto.setAuthorName(book.getAuthorName());
        dto.setFirstPublishYear(book.getFirstPublishYear());
        dto.setEditionCount(book.getEditionCount());
        dto.setHasFulltext(book.getHasFulltext());
        dto.setPrice(book.getPrice());
        dto.setStockQuantity(book.getStockQuantity());
        dto.setAvailableQuantity(book.getAvailableQuantity());
        return dto;
    }
}

