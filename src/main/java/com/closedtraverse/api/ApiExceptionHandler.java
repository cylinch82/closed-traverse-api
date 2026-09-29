package com.closedtraverse.api;

import com.closedtraverse.api.TraverseModels.ErrorResponse;
import com.closedtraverse.calculation.InvalidTraverseException;
import com.closedtraverse.service.TraverseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidTraverseException.class)
    public ResponseEntity<ErrorResponse> invalid(InvalidTraverseException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_TRAVERSE", ex.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "Request is malformed."));
    }

    @ExceptionHandler(TraverseNotFoundException.class)
    public ResponseEntity<ErrorResponse> missing(TraverseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("TRAVERSE_NOT_FOUND", ex.getMessage()));
    }
}
