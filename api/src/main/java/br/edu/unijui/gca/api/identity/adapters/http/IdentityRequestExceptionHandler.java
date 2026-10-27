package br.edu.unijui.gca.api.identity.adapters.http;

import br.edu.unijui.gca.api.dtos.ErrorResponseDto;
import br.edu.unijui.gca.api.identity.adapters.http.dto.IdentityRequest;
import br.edu.unijui.gca.api.metadata.adapters.http.ApiKeyResource;
import br.edu.unijui.gca.api.metadata.adapters.http.UserResource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Profile("metadata-management")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AuthResource.class, UserResource.class, ApiKeyResource.class})
public class IdentityRequestExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> invalidFields(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().collect(Collectors.joining("; "));
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> invalidBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof IdentityRequest.InvalidField invalid) {
                return badRequest("Invalid request field: " + invalid.field());
            }
            if (cause instanceof IdentityRequest.UnknownField unknown) {
                return badRequest("Unknown request field: " + unknown.field());
            }
        }
        return badRequest("Invalid request body");
    }

    private ResponseEntity<ErrorResponseDto> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDto(message));
    }
}
