package com.mvelelo.licensing.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.mvelelo.licensing.api")
class ApiExceptionHandler {

    @ExceptionHandler(LicenseException.class)
    ResponseEntity<ErrorResponse> handle(LicenseException e) {
        return ResponseEntity.status(e.status())
                .body(new ErrorResponse(e.code().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", msg));
    }
}
