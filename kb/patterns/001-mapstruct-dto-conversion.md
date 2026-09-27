---
name: MapStruct DTO Conversion Pattern
description: Use MapStruct for entity-to-DTO conversions with explicit mappers
category: patterns
last-updated: 2026-09-27
---

# MapStruct DTO Conversion Pattern

## Pattern
Use `@Mapper` (MapStruct) for all entity ↔ DTO conversions. Never return entities from resources; always map to DTOs.

## Rationale

- **Decoupling**: Frontend depends on DTOs, not entities
- **Flexibility**: Can evolve entities without breaking API contracts
- **Security**: Control which fields are exposed to clients
- **Composition**: Combine multiple entities into single DTO

## Mapper Definition

```java
@Mapper(componentModel = "jakarta")
public interface AppointmentMapper {
    
    @Mapping(source = "organizer.id", target = "organizerId")
    @Mapping(source = "organizer.displayName", target = "organizerName")
    AppointmentDto toDto(Appointment entity);
    
    Appointment toEntity(CreateAppointmentRequest dto);
    
    List<AppointmentDto> toDtos(List<Appointment> entities);
}
```

## Usage in Resources

```java
@Path("/appointments")
@ApplicationScoped
public class AppointmentResource {
    @Inject
    AppointmentService service;
    
    @Inject
    AppointmentMapper mapper;
    
    @GET
    @Path("/{id}")
    public AppointmentDto getAppointment(@PathParam Long id) {
        Appointment entity = service.getAppointment(id);
        return mapper.toDto(entity);  // Always map to DTO
    }
}
```

## Rules

1. **One-way mappings**: Mappers only convert, never call services
2. **Explicit mappings**: Use `@Mapping` for non-obvious field mappings
3. **Null handling**: Use `nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL`
4. **Custom conversions**: If logic is complex, create a custom `@Mapping` method
5. **Component model**: Always use `componentModel = "jakarta"` for CDI integration

## No Entity Returns

❌ **Never return entities:**
```java
@GET
@Path("/{id}")
public Appointment bad() {
    return appointmentRepository.findById(id);  // WRONG!
}
```

✅ **Always use DTOs:**
```java
@GET
@Path("/{id}")
public AppointmentDto good() {
    Appointment entity = appointmentRepository.findById(id);
    return mapper.toDto(entity);  // Correct
}
```

## DTO Design

Keep DTOs minimal:
- Include only fields needed by the API consumer
- Use nested DTOs for related entities (not full entity graphs)
- Flatten deeply nested structures
- Mark nullable fields in type system (`Optional<T>`)
