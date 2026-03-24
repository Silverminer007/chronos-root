package de.chronos_live.chronos_date_api.application;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LeaderElectionService}.
 *
 * <p>Strategy: {@code @QuarkusTest} is used because the service is a CDI bean.
 * The application property {@code scheduler.leader-election.enabled=false} in
 * {@code src/test/resources/application.properties} disables leader election
 * for the entire test suite, so no real Kubernetes client interaction ever
 * occurs. {@link KubernetesClient} is mocked via {@code @InjectMock} to
 * prevent the CDI container from requiring a live cluster during startup.
 *
 * <p><b>Untestable branches:</b>
 * <ul>
 *   <li>{@code onStart} lines 42–83: The {@code enabled=true} path starts a
 *       background thread that calls
 *       {@code kubernetesClient.leaderElector()...run()}, which requires a
 *       live Kubernetes API server (or a sufficiently deep mock of the Fabric8
 *       client). Re-enabling leader election in a unit test would block
 *       indefinitely and is therefore out of scope. This path would be covered
 *       by a dedicated integration / end-to-end test.</li>
 *   <li>{@code onStop} lines 87–91: The {@code enabled=true} branch cancels
 *       the background future. It is only reachable after {@code onStart} with
 *       {@code enabled=true} has run, so it is untestable for the same reason.</li>
 *   <li>Inner lambda – exception handler line 80–82: the catch block that sets
 *       {@code leader = false} after a leader-election failure is only reachable
 *       from the background thread started in the {@code enabled=true} path.</li>
 * </ul>
 *
 * <p>All testable branches (the {@code enabled=false} fast-paths and the
 * {@code isLeader()} logic) are fully covered below.
 */
@QuarkusTest
class LeaderElectionServiceTest {

    // ── CDI injection ─────────────────────────────────────────────────────────
    @Inject
    LeaderElectionService service;

    /**
     * The KubernetesClient is mocked so that Quarkus does not attempt to
     * connect to a real cluster when the CDI bean is instantiated.
     */
    @InjectMock
    KubernetesClient kubernetesClient;

    // ══════════════════════════════════════════════════════════════════════════
    // isLeader
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – isLeader:
     *   B1  !enabled → return true  (election disabled, every instance is leader)
     *   B2  enabled && leader == true  → return true
     *   B3  enabled && leader == false → return false
     *
     * With {@code scheduler.leader-election.enabled=false} set in the test
     * application.properties, {@code enabled} is {@code false} for this entire
     * test class; therefore B1 is the only reachable branch here. B2 and B3
     * require {@code enabled=true} which triggers the Kubernetes path (see
     * class-level Javadoc — untestable in unit tests).
     *
     * Total testable branches: 1  |  Tests: 1
     */
    @Nested
    class IsLeader {

        // B1 – leader election disabled → isLeader() must return true
        @Test
        void should_returnTrue_when_leaderElectionIsDisabled() {
            // With scheduler.leader-election.enabled=false, the service never
            // starts the election loop; the formula is: !enabled || leader
            // = !false || false = true
            assertThat(service.isLeader()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onStart (enabled=false fast-path)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onStart fast-path:
     *   B1  !enabled → log and return immediately (no Kubernetes calls)
     *
     * The service has already been started by the CDI container before the
     * test runs. We verify the observable side-effect: no Kubernetes client
     * method was called (because enabled=false causes an early return).
     *
     * Total branches: 1  |  Tests: 1
     */
    @Nested
    class OnStart {

        @Test
        void should_notInteractWithKubernetesClient_when_leaderElectionIsDisabled() {
            // The CDI container called onStart() during application startup.
            // Because enabled=false, kubernetesClient must have never been touched.
            org.mockito.Mockito.verifyNoInteractions(kubernetesClient);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // onStop (enabled=false fast-path)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage plan – onStop fast-path:
     *   B1  !enabled → immediate return (no future/executor manipulation)
     *
     * We call onStop() directly to exercise the early-return branch. The method
     * is package-private (visible within the same package), which is satisfied
     * by this test class residing in the same package.
     *
     * Total branches: 1  |  Tests: 1
     */
    @Nested
    class OnStop {

        @Test
        void should_returnImmediately_when_leaderElectionIsDisabled() {
            // Calling onStop() directly; with enabled=false it should just return
            // without throwing or interacting with any executor/future.
            service.onStop(new io.quarkus.runtime.ShutdownEvent());
            // No exception = pass. Kubernetes client was still never called.
            org.mockito.Mockito.verifyNoInteractions(kubernetesClient);
        }
    }
}
