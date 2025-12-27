package de.chronos_live.chronos_date_api.exception;

import de.chronos_live.chronos_date_api.dto.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ValidationException exception) {
        Log.warn("Validation error: " + exception.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .errorCode(exception.getErrorCode())
                .message(exception.getMessage())
                .path(uriInfo.getPath())
                .fieldErrors(exception.getFieldErrors())
                .build();

        return Response.status(400).entity(error).build();
    }
}