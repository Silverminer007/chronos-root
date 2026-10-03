# ADR 0010: Split EventBus Trait for Dyn Compatibility

**Date:** 2026-10-03  
**Status:** Accepted  
**Context:** The `EventBus` trait was not dyn-compatible due to a generic method `subscribe<F>`, which prevented using `Arc<dyn EventBus>` throughout the codebase.  
**Decision:** Split the trait into two complementary traits:
- **`EventPublisher`**: Contains the `fire(&self, event: Event)` method — dyn-compatible and used by services/app state
- **`EventSubscriber`**: Contains the generic `subscribe<F>` method — used only in setup code that has access to the concrete type

**Rationale:** 
- Services and handlers only need to publish events; they don't subscribe
- Subscription setup happens once at app initialization with access to `PostgresEventBus`
- This separation follows the Single Responsibility Principle while maintaining the event-driven architecture

**Implementation:**
- `PostgresEventBus` implements both traits
- All service dependencies take `Arc<dyn EventPublisher>` instead of `Arc<dyn EventBus>`
- App initialization code can access the concrete type for subscription
