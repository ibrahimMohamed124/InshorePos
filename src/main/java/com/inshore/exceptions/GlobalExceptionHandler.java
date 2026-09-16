package com.inshore.exceptions;

import com.inshore.payload.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResponse> handleUserException(UserException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CannotCreateAdminUserException.class)
    public ResponseEntity<ApiResponse> handleCannotCreateAdmin(CannotCreateAdminUserException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private ResponseEntity<ApiResponse> build(HttpStatus status, String message) {
        ApiResponse response = new ApiResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return ResponseEntity.status(status).body(response);
    }
}