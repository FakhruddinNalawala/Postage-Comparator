package com.postage.postagecomparator.api;

import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "BAD_REQUEST");
        errorDetails.put("message", ex.getMessage() != null ? ex.getMessage() : "Bad request");
        errorDetails.put("timestamp", Instant.now().toString());
        
        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "BAD_REQUEST");

        var fieldError = ex.getBindingResult().getFieldError();
        var message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Validation failed";

        errorDetails.put("message", message);
        errorDetails.put("timestamp", Instant.now().toString());

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "BAD_REQUEST");
        errorDetails.put("message", "Malformed JSON request body");
        errorDetails.put("timestamp", Instant.now().toString());

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "BAD_REQUEST");
        errorDetails.put("message", "Invalid value for parameter '" + ex.getName() + "'");
        errorDetails.put("timestamp", Instant.now().toString());

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "BAD_REQUEST");

        var message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage() != null ? v.getMessage() : "Validation failed")
                .orElse("Validation failed");

        errorDetails.put("message", message);
        errorDetails.put("timestamp", Instant.now().toString());

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "INTERNAL_ERROR");
        errorDetails.put("message", ex.getMessage() != null ? ex.getMessage() : "Internal server error");
        errorDetails.put("timestamp", Instant.now().toString());

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "BAD_REQUEST");
        errorDetails.put("message", ex.getMessage() != null ? ex.getMessage() : "Bad request");
        errorDetails.put("timestamp", Instant.now().toString());

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        var errorDetails = new HashMap<String, Object>();
        errorDetails.put("code", "NOT_FOUND");
        errorDetails.put("message", ex.getMessage() != null ? ex.getMessage() : "Resource not found");
        errorDetails.put("timestamp", Instant.now().toString());
        
        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("error", errorDetails);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
}
