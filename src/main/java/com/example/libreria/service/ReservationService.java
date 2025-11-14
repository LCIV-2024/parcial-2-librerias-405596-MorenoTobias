package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día
    
    private final ReservationRepository reservationRepository;
    private final BookService bookService;
    private final UserService userService;
    
    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {
        // Validar que el usuario existe
        User user = userService.getUserEntity(requestDTO.getUserId());
        
        // Validar que el libro existe y está disponible
        Book book = bookService.getBookEntityByExternalId(requestDTO.getBookExternalId());
        
        if (book.getAvailableQuantity() <= 0) {
            throw new RuntimeException("No hay libros disponibles para reservar");
        }
        
        // Crear la reserva
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setBook(book);
        reservation.setRentalDays(requestDTO.getRentalDays());
        reservation.setStartDate(requestDTO.getStartDate());
        reservation.setExpectedReturnDate(requestDTO.getStartDate().plusDays(requestDTO.getRentalDays()));
        reservation.setDailyRate(book.getPrice());
        reservation.setTotalFee(calculateTotalFee(book.getPrice(), requestDTO.getRentalDays()));
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        
        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("Created reservation with id: {} for user: {} and book: {}", 
                savedReservation.getId(), user.getId(), book.getExternalId());
        
        // Reducir la cantidad disponible
        bookService.decreaseAvailableQuantity(requestDTO.getBookExternalId());
        
        return convertToDTO(savedReservation);
    }
    
    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));
        
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }
        
        LocalDate returnDate = returnRequest.getReturnDate();
        reservation.setActualReturnDate(returnDate);
        
        // Calcular tarifa por demora si hay retraso
        LocalDate expectedReturnDate = reservation.getExpectedReturnDate();
        if (returnDate.isAfter(expectedReturnDate)) {
            long daysLate = ChronoUnit.DAYS.between(expectedReturnDate, returnDate);
            BigDecimal lateFee = calculateLateFee(reservation.getBook().getPrice(), daysLate);
            reservation.setLateFee(lateFee);
            reservation.setStatus(Reservation.ReservationStatus.OVERDUE);
            log.info("Reservation {} returned {} days late, late fee: {}", reservationId, daysLate, lateFee);
        } else {
            reservation.setLateFee(BigDecimal.ZERO);
            reservation.setStatus(Reservation.ReservationStatus.RETURNED);
        }
        
        Reservation savedReservation = reservationRepository.save(reservation);
        
        // Aumentar la cantidad disponible
        bookService.increaseAvailableQuantity(reservation.getBook().getExternalId());
        
        log.info("Book returned for reservation id: {}", reservationId);
        return convertToDTO(savedReservation);
    }
    
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        return reservationRepository.findOverdueReservations(LocalDate.now()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {
        return dailyRate.multiply(new BigDecimal(rentalDays))
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        // 15% del precio del libro por cada día de demora
        BigDecimal dailyLateFee = bookPrice.multiply(LATE_FEE_PERCENTAGE);
        return dailyLateFee.multiply(new BigDecimal(daysLate))
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}

