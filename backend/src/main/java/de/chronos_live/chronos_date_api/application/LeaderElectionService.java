package de.chronos_live.chronos_date_api.application;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@ApplicationScoped
public class LeaderElectionService {

    private static final Logger LOG = Logger.getLogger(LeaderElectionService.class);
    private static final String LEASE_NAME = "chronos-scheduler";

    @Inject
    KubernetesClient kubernetesClient;

    @ConfigProperty(name = "scheduler.leader-election.enabled", defaultValue = "true")
    boolean enabled;

    private volatile boolean leader = false;
    private ExecutorService executorService;
    private Future<?> leaderElectionFuture;

    public boolean isLeader() {
        return !enabled || leader;
    }

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            LOG.info("Leader election is disabled; all instances will run scheduled tasks");
            return;
        }
        String identity = System.getenv("HOSTNAME");
        if (identity == null || identity.isBlank()) {
            identity = UUID.randomUUID().toString();
        }
        String namespace = kubernetesClient.getNamespace();
        String resolvedIdentity = identity;

        LOG.infof("Starting leader election: lease=%s, namespace=%s, identity=%s",
                LEASE_NAME, namespace, resolvedIdentity);

        var lock = new LeaseLock(namespace, LEASE_NAME, resolvedIdentity);
        var config = new LeaderElectionConfigBuilder()
                .withName(LEASE_NAME)
                .withLock(lock)
                .withLeaseDuration(Duration.ofSeconds(15))
                .withRenewDeadline(Duration.ofSeconds(10))
                .withRetryPeriod(Duration.ofSeconds(2))
                .withReleaseOnCancel()
                .withLeaderCallbacks(new LeaderCallbacks(
                        () -> { leader = true;  LOG.infof("Pod %s acquired scheduler leadership", resolvedIdentity); },
                        () -> { leader = false; LOG.warnf("Pod %s lost scheduler leadership", resolvedIdentity); },
                        newLeader -> LOG.infof("New scheduler leader: %s", newLeader)
                ))
                .build();

        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "leader-election");
            t.setDaemon(false);
            return t;
        });
        leaderElectionFuture = executorService.submit(() -> {
            try {
                kubernetesClient.leaderElector().withConfig(config).build().run();
            } catch (Exception e) {
                LOG.errorf(e, "Leader election failed: %s", e.getMessage());
                leader = false;
            }
        });
    }

    void onStop(@Observes ShutdownEvent event) {
        if (!enabled) return;
        leader = false;
        if (leaderElectionFuture != null) leaderElectionFuture.cancel(true);
        if (executorService != null) executorService.shutdownNow();
    }
}
