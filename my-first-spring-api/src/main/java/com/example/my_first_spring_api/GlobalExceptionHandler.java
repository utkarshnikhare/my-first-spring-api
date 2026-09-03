package com.example.my_first_spring_api;

import com.example.my_first_spring_api.dto.ApiErrorDto;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.InvalidKitchenSelectionException;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.exception.OtpVerificationException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.exception.TemplateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({OrderNotFoundException.class, ProductNotFoundException.class,
            KitchenNotFoundException.class, TemplateNotFoundException.class})
    public ResponseEntity<ApiErrorDto> handleNotFound(RuntimeException ex) {
        return new ResponseEntity<>(new ApiErrorDto("NOT_FOUND", ex.getMessage(), 404), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BuyerNotAuthenticatedException.class)
    public ResponseEntity<ApiErrorDto> handleUnauthenticated(BuyerNotAuthenticatedException ex) {
        return new ResponseEntity<>(new ApiErrorDto("UNAUTHORIZED", ex.getMessage(), 401), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(SellerNotAuthorizedException.class)
    public ResponseEntity<ApiErrorDto> handleForbidden(SellerNotAuthorizedException ex) {
        return new ResponseEntity<>(new ApiErrorDto("FORBIDDEN", ex.getMessage(), 403), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorDto> handleConflict(IllegalStateException ex) {
        return new ResponseEntity<>(new ApiErrorDto("CONFLICT", ex.getMessage(), 409), HttpStatus.CONFLICT);
    }

    @ExceptionHandler({OtpVerificationException.class, InvalidKitchenSelectionException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorDto> handleBadRequest(RuntimeException ex) {
        logger.warn("Bad request: {}", ex.getMessage());
        return new ResponseEntity<>(new ApiErrorDto("BAD_REQUEST", ex.getMessage(), 400), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorDto> handleBadRequestWeb(Exception ex) {
        return new ResponseEntity<>(new ApiErrorDto("BAD_REQUEST", "Invalid request. Please check the submitted data.", 400), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorDto> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return new ResponseEntity<>(new ApiErrorDto("METHOD_NOT_ALLOWED", "This HTTP method is not supported for the requested endpoint.", 405), HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorDto> handleNoResourceFound(NoResourceFoundException ex) {
        return new ResponseEntity<>(new ApiErrorDto("NOT_FOUND", "The requested endpoint does not exist.", 404), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(new ApiErrorDto("INTERNAL_ERROR", "An unexpected error occurred. Please try again.", 500), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

