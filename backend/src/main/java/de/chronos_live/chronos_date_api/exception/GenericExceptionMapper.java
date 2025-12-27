package de.chronos_live.chronos_date_api.exception;

import de.chronos_live.chronos_date_api.dto.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        Log.error("Unhandled exception", exception);

        // In Produktion: Keine Stack-Traces an Client senden!
        ErrorResponse error = ErrorResponse.builder()
                .status(500)
                .error("Internal Server Error")
                .errorCode("INTERNAL_ERROR")
                .message("Ein interner Fehler ist aufgetreten")
                .path(uriInfo.getPath())
                .build();

        return Response.status(500).entity(error).build();
    }
}