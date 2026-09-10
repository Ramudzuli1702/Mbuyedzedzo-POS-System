package com.mvelelo.licensing.admin.web;

import com.mvelelo.licensing.admin.CustomerAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice(basePackages = "com.mvelelo.licensing.admin.web")
class AdminExceptionHandler {

    @ExceptionHandler(CustomerAdminService.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String denied() {
        return "error/403";
    }
}
