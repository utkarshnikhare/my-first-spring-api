package com.example.my_first_spring_api;

import com.example.my_first_spring_api.dto.ApiErrorDto;
import com.example.my_first_spring_api.exception.BuyerNotAuthenticatedException;
import com.example.my_first_spring_api.exception.BuyerProfileIncompleteException;
import com.example.my_first_spring_api.exception.AccountAlreadyExistsException;
import com.example.my_first_spring_api.exception.InvalidCredentialsException;
import com.example.my_first_spring_api.exception.InvalidKitchenSelectionException;
import com.example.my_first_spring_api.exception.KitchenNotEligibleException;
import com.example.my_first_spring_api.exception.KitchenNotFoundException;
import com.example.my_first_spring_api.exception.OrderNotFoundException;
import com.example.my_first_spring_api.exception.ProductNotFoundException;
import com.example.my_first_spring_api.exception.SellerNotAuthorizedException;
import com.example.my_first_spring_api.exception.TemplateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean-validation failures.
     *
     * <p>Two defects fixed here. (1) This handler used to answer with a bare
     * {@code Map<String,String>} while every other handler answers with
     * {@link ApiErrorDto}, so clients had to parse two different error shapes from one
     * service; it now returns the same DTO as everything else. (2) It used to cast every
     * binding error to {@code FieldError}. {@code getAllErrors()} yields {@code ObjectError},
     * and a class-level constraint (for example {@code @AssertTrue} on the DTO) produces a
     * plain {@code ObjectError} - the cast then threw inside the exception handler and turned
     * a 400 into a 500. The {@code instanceof} guard makes the handler total.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        String general = null;
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String message = error.getDefaultMessage();
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), message);
            } else {
                // Object-level (class-level) constraint: no field name exists.
                general = message;
            }
        }
        if (!errors.isEmpty()) {
            return new ResponseEntity<>(new ApiErrorDto("VALIDATION_ERROR",
                    "Invalid request fields: " + errors, 400), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(new ApiErrorDto("VALIDATION_ERROR",
                general != null ? general : "The submitted data is invalid.", 400),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, String>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getParameterValidationResults().forEach(err -> {
            String field = err.getMethodParameter().getParameterName();
            String message = err.getResolvableErrors().isEmpty() ? "Invalid value" : err.getResolvableErrors().get(0).getDefaultMessage();
            errors.put(field != null ? field : "request", message);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({OrderNotFoundException.class, ProductNotFoundException.class,
            KitchenNotFoundException.class, TemplateNotFoundException.class})
    public ResponseEntity<ApiErrorDto> handleNotFound(RuntimeException ex) {
        return new ResponseEntity<>(new ApiErrorDto("NOT_FOUND", ex.getMessage(), 404), HttpStatus.NOT_FOUND);
    }

    /**
     * An ineligible kitchen keeps the same 404 as a missing one — concealment is
     * intentional — but carries its own code and the exact user-facing wording,
     * so the kitchen page can explain the reason and offer "Explore kitchens"
     * instead of implying the kitchen simply does not exist.
     */
    @ExceptionHandler(KitchenNotEligibleException.class)
    public ResponseEntity<ApiErrorDto> handleKitchenNotEligible(KitchenNotEligibleException ex) {
        return new ResponseEntity<>(new ApiErrorDto(KitchenNotEligibleException.CODE, ex.getMessage(), 404),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BuyerNotAuthenticatedException.class)
    public ResponseEntity<ApiErrorDto> handleUnauthenticated(BuyerNotAuthenticatedException ex) {
        return new ResponseEntity<>(new ApiErrorDto("UNAUTHORIZED", ex.getMessage(), 401), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BuyerProfileIncompleteException.class)
    public ResponseEntity<ApiErrorDto> handleProfileIncomplete(BuyerProfileIncompleteException ex) {
        return new ResponseEntity<>(new ApiErrorDto("PROFILE_INCOMPLETE", ex.getMessage(), 422), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(SellerNotAuthorizedException.class)
    public ResponseEntity<ApiErrorDto> handleForbidden(SellerNotAuthorizedException ex) {
        return new ResponseEntity<>(new ApiErrorDto("FORBIDDEN", ex.getMessage(), 403), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ApiErrorDto> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
        return new ResponseEntity<>(new ApiErrorDto("ACCOUNT_ALREADY_EXISTS", ex.getMessage(), 409), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ResponseEntity<>(new ApiErrorDto("INVALID_CREDENTIALS", ex.getMessage(), 401), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorDto> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        return new ResponseEntity<>(new ApiErrorDto("REQUEST_REJECTED", message, status.value()), status);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorDto> handleConflict(IllegalStateException ex) {
        return new ResponseEntity<>(new ApiErrorDto("CONFLICT", ex.getMessage(), 409), HttpStatus.CONFLICT);
    }

    @ExceptionHandler({InvalidKitchenSelectionException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorDto> handleBadRequest(RuntimeException ex) {
        logger.warn("Bad request: {}", ex.getMessage());
        return new ResponseEntity<>(new ApiErrorDto("BAD_REQUEST", ex.getMessage(), 400), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorDto> handleBadRequestWeb(Exception ex) {
        logger.error("Bad request body binding: ", ex);
        return new ResponseEntity<>(new ApiErrorDto("BAD_REQUEST", "Invalid request. Please check the submitted data.", 400), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiErrorDto> handleDateTimeParse(DateTimeParseException ex) {
        return new ResponseEntity<>(new ApiErrorDto("BAD_REQUEST", "Invalid date supplied. Use yyyy-MM-dd or 'today'/'tomorrow'.", 400), HttpStatus.BAD_REQUEST);
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
