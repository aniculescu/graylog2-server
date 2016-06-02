package org.graylog2.security;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.realm.Realm;
import org.graylog2.cluster.ClusterConfigChangedEvent;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.utilities.LenientExplicitOrdering;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Runtime (re-)orderable collection of Shiro AuthenticatingRealms.
 *
 * The generic type is Realm, even though it really only contains AuthenticatingRealms. This is simply to avoid having to
 * cast the generic collection when passing it to the SecurityManager.
 */
public class OrderedAuthenticatingRealms extends AbstractCollection<Realm> {

    private final Map<String, AuthenticatingRealm> availableRealms;
    private final ClusterConfigService clusterConfigService;

    private final AtomicReference<List<Realm>> orderedRealms = new AtomicReference<>();

    @Inject
    public OrderedAuthenticatingRealms(Map<String, AuthenticatingRealm> realms,
                                       ClusterConfigService clusterConfigService,
                                       EventBus eventBus) {
        this.availableRealms = realms;
        this.clusterConfigService = clusterConfigService;
        eventBus.register(this);

        sortRealms();
        // sortRealms should have produced a reasonable default
        Objects.requireNonNull(orderedRealms.get());
    }

    @Subscribe
    public void handleOrderingUpdate(ClusterConfigChangedEvent event) {
        if (!AuthenticationConfig.class.getCanonicalName().equals(event.type())) {
            return;
        }

        sortRealms();
    }

    private void sortRealms() {
        final AuthenticationConfig config = clusterConfigService.getOrDefault(AuthenticationConfig.class,
                                                                              AuthenticationConfig.defaultInstance());

        final LenientExplicitOrdering<String> ordering = new LenientExplicitOrdering<>(config.realmOrder());

        final ImmutableList<String> newRealmOrder = ordering.immutableSortedCopy(availableRealms.keySet());
        orderedRealms.set(newRealmOrder.stream().map(availableRealms::get).collect(Collectors.toList()));
    }

    @Nonnull
    @Override
    public Iterator<Realm> iterator() {
        return orderedRealms.get().iterator();
    }

    @Override
    public int size() {
        return orderedRealms.get().size();
    }
}
