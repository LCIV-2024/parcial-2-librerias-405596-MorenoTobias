package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    
    @Mock
    private ReservationRepository reservationRepository;
    
    @Mock
    private BookService bookService;
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private ReservationService reservationService;
    
    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");
        
        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);
        
        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());
    }
    
    @Test
    void testCreateReservation_Success() {
        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setRentalDays(7);
        requestDTO.setStartDate(LocalDate.now());
        
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookService.getBookEntityByExternalId(258027L)).thenReturn(testBook);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        doNothing().when(bookService).decreaseAvailableQuantity(258027L);
        
        // Act
        ReservationResponseDTO result = reservationService.createReservation(requestDTO);
        
        // Assert
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
        verify(userService, times(1)).getUserEntity(1L);
        verify(bookService, times(1)).getBookEntityByExternalId(258027L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).decreaseAvailableQuantity(258027L);
    }
    
    @Test
    void testCreateReservation_BookNotAvailable() {
        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setRentalDays(7);
        requestDTO.setStartDate(LocalDate.now());
        
        testBook.setAvailableQuantity(0);
        
        when(userService.getUserEntity(1L)).thenReturn(testUser);
        when(bookService.getBookEntityByExternalId(258027L)).thenReturn(testBook);
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.createReservation(requestDTO);
        });
        
        assertEquals("No hay libros disponibles para reservar", exception.getMessage());
        verify(userService, times(1)).getUserEntity(1L);
        verify(bookService, times(1)).getBookEntityByExternalId(258027L);
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(bookService, never()).decreaseAvailableQuantity(anyLong());
    }
    
    @Test
    void testReturnBook_OnTime() {
        // Arrange
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now().plusDays(5)); // Devuelto 2 días antes
        
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);
        doNothing().when(bookService).increaseAvailableQuantity(258027L);
        
        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);
        
        // Assert
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
        verify(reservationRepository, times(1)).findById(1L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).increaseAvailableQuantity(258027L);
    }
    
    @Test
    void testReturnBook_Overdue() {
        // Arrange
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now().plusDays(10)); // Devuelto 3 días tarde
        
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        doNothing().when(bookService).increaseAvailableQuantity(258027L);
        
        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);
        
        // Assert
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
        verify(reservationRepository, times(1)).findById(1L);
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(bookService, times(1)).increaseAvailableQuantity(258027L);
        
        // Verificar que se calculó la multa
        verify(reservationRepository).save(argThat(reservation -> 
            reservation.getLateFee() != null && 
            reservation.getLateFee().compareTo(BigDecimal.ZERO) > 0 &&
            reservation.getStatus() == Reservation.ReservationStatus.OVERDUE
        ));
    }
    
    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        
        ReservationResponseDTO result = reservationService.getReservationById(1L);
        
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }
    
    @Test
    void testGetAllReservations() {
		Book testBook2 = new Book();
		testBook2.setExternalId(258027L);
		testBook2.setTitle("The Lord of the Rings");
		testBook2.setPrice(new BigDecimal("15.99"));
		testBook2.setStockQuantity(10);
		testBook2.setAvailableQuantity(5);

		Reservation testReservation2 = new Reservation();
		testReservation2.setId(1L);
		testReservation2.setUser(testUser);
		testReservation2.setBook(testBook2);
		testReservation2.setRentalDays(7);
		testReservation2.setStartDate(LocalDate.now());
		testReservation2.setExpectedReturnDate(LocalDate.now().plusDays(14));
		testReservation2.setDailyRate(new BigDecimal("15.99"));
		testReservation2.setTotalFee(new BigDecimal("111.93"));
		testReservation2.setStatus(Reservation.ReservationStatus.ACTIVE);
		testReservation2.setCreatedAt(LocalDateTime.now());

        when(reservationRepository.findAll()).thenReturn(Arrays.asList(testReservation, testReservation2));
        
        List<ReservationResponseDTO> result = reservationService.getAllReservations();
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }
    
    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getActiveReservations();
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

