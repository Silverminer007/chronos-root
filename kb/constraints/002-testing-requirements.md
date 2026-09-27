---
name: Testing Requirements
description: TDD-driven testing with Testcontainers integration tests and mandatory coverage
category: constraints
last-updated: 2026-09-27
---

# Testing Requirements

## Testing Strategy

Chronos follows **Test-Driven Development (TDD)**:
1. Write failing test (red)
2. Implement minimal code to pass (green)
3. Refactor to improve quality (blue)

All new features must include tests before implementation.

## Test Types

### Unit Tests (Recommended)
- Test services in isolation
- Mock repositories and external dependencies
- Focus on business logic, not framework

**Example**:
```java
@Test
public void testCreateAppointment() {
    // Arrange
    AppointmentService service = new AppointmentService();
    service.appointmentRepository = mock(AppointmentRepository.class);
    service.principalContext = mock(PrincipalContext.class);
    when(principalContext.getUserId()).thenReturn(1L);
    
    // Act
    Long id = service.createAppointment(new CreateAppointmentRequest(...));
    
    // Assert
    assertEquals(1L, id);
    verify(appointmentRepository).persist(any());
}
```

### Integration Tests (Required for Data Access)
- Use Testcontainers to spin up PostgreSQL
- Test against real database
- Verify ORM mappings, query logic

**Example**:
```java
@QuarkusTest
public class AppointmentRepositoryTest {
    @Inject
    AppointmentRepository repository;
    
    @Test
    @Transactional
    public void testFindByOrganizer() {
        // Testcontainers handles DB
        Appointment apt = new Appointment(...);
        repository.persist(apt);
        
        List<Appointment> found = repository.findByOrganizerId(1L);
        assertNotEmpty(found);
    }
}
```

### End-to-End Tests (For Critical Paths)
- Use Playwright for frontend E2E tests
- Test full user workflows
- Limited use (slow, brittle)

## Test Coverage

**Minimum standards**:
- `./mvnw test` must pass
- **Backend**: 70%+ line coverage (enforced by CI)
- **Critical paths**: 90%+ coverage (appointment creation, auth, notifications)
- **Data layer**: 100% (all query methods tested)

**Run coverage report**:
```bash
./mvnw verify jacoco:report
# Open: target/site/jacoco/index.html
```

## Testcontainers

Quarkus integrates Testcontainers for integration tests:

```java
@QuarkusTest
public class AppointmentServiceTest {
    // Testcontainers starts PostgreSQL automatically
    // No manual setup required
    
    @Inject
    AppointmentService service;
    
    @Test
    public void testEndToEnd() {
        // Real database transaction
        Long id = service.createAppointment(...);
        assertNotNull(id);
    }
}
```

**Benefits**:
- Real database (not mocked)
- Automatic start/stop
- Isolated per test
- Reproducible environment

## Test Naming Convention

Use `test<Feature><Condition><Expected>`:

```java
@Test
public void testCreateAppointmentWithValidDataSucceeds() { ... }

@Test
public void testCreateAppointmentWithPastDateThrowsException() { ... }

@Test
public void testFindAppointmentReturnsEmptyListWhenNoneExist() { ... }
```

## Assertions

Use assertJ for fluent assertions:

```java
// ❌ JUnit (terse)
assertEquals(expectedList, actualList);

// ✅ AssertJ (fluent)
assertThat(actualList)
    .hasSize(2)
    .contains(expectedItem1, expectedItem2)
    .allMatch(apt -> apt.isPastAppointment() == false);
```

## Mocking Policy

- **Unit tests**: Mock all external dependencies (repositories, services, CDI beans)
- **Integration tests**: Use real components; don't mock
- **E2E tests**: Never mock; test against real systems

**Tool**: Mockito

```java
// ❌ Don't mock repository in integration tests
@QuarkusTest
public class Test {
    @Inject
    AppointmentRepository repository;  // Real!
    
    @Test
    public void test() {
        // This uses real DB, good
    }
}

// ✅ Mock only in unit tests
public class ServiceUnitTest {
    private AppointmentRepository repository = mock(AppointmentRepository.class);
    
    @Test
    public void test() {
        // This mocks DB, good for unit test
    }
}
```

## CI Requirements

**All tests must pass before merge**:
```bash
./mvnw test           # Unit + integration tests
./mvnw verify         # Plus code quality gates
```

**Failure stops PR**: No skipping tests for merge.

## Rules

1. **TDD first**: Write test before code
2. **Coverage matters**: 70%+ or CI fails
3. **Real databases**: Integration tests use Testcontainers
4. **Clear names**: Test names describe what's being tested
5. **No test code duplication**: Use `@BeforeEach` for setup
6. **Assertions first**: Check behavior, not implementation
7. **Fast unit tests**: < 1ms each (mocking required)
8. **Slow integration tests**: OK if < 5s (Testcontainers overhead)
