package de.chronos_live.chronos_date_api.exception;

import de.chronos_live.chronos_date_api.dto.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper für ResourceNotFoundException → 404
 */
@Provider
public class ResourceNotFoundExceptionMapper
        implements ExceptionMapper<ResourceNotFoundException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ResourceNotFoundException exception) {
        Log.warn("Resource not found: " + exception.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(404)
                .error("Not Found")
                .errorCode(exception.getErrorCode())
                .message(exception.getMessage())
                .path(uriInfo.getPath())
                .build();

        return Response.status(404).entity(error).build();
    }
}

