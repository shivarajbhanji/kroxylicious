/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.proxy.internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.kroxylicious.proxy.model.VirtualCluster;
import io.kroxylicious.proxy.service.HostPort;

/**
 * Detects potential for port conflicts arising between virtual cluster configurations.
 */
public class PortConflictDetector {

    private final static Optional<String> ANY_INTERFACE = Optional.empty();
    private static final String ANY_STRING = "<any>";

    private enum BindingScope {
        SHARED,
        EXCLUSIVE
    }

    /**
     * Validates the configuration throwing an exception if a conflict is detected.
     *
     * @param virtualClusterMap map of virtual clusters.
     * @param otherExclusivePort an optional exclusive port that should conflict with virtual cluster ports
     */
    public void validate(Collection<VirtualCluster> virtualClusterMap, Optional<HostPort> otherExclusivePort) {

        Set<String> seenVirtualClusters = new HashSet<>();

        Map<Optional<String>, Set<Integer>> inUseExclusivePorts = new HashMap<>();
        Map<Optional<String>, Map<Integer, Boolean>> inUseSharedPorts = new HashMap<>();

        virtualClusterMap.stream()
                .sorted(Comparator.comparing(VirtualCluster::getClusterName))
                .forEach(virtualCluster -> {
            var name = virtualCluster.getClusterName();
            var proposedSharedPorts = virtualCluster.getSharedPorts();
            var proposedExclusivePorts = virtualCluster.getExclusivePorts();

            if (otherExclusivePort.isPresent()) {
                Optional<String> otherInterface = otherExclusivePort.map(hostPort -> hostPort.host().equals("0.0.0.0") ? null : hostPort.host());
                var checkPorts = virtualCluster.getBindAddress().isEmpty() || otherInterface.isEmpty() || virtualCluster.getBindAddress().equals(otherInterface);
                if (checkPorts) {
                    checkForConflictsWithOtherExclusivePort(otherExclusivePort.get(), name, virtualCluster, proposedExclusivePorts, BindingScope.EXCLUSIVE);
                    checkForConflictsWithOtherExclusivePort(otherExclusivePort.get(), name, virtualCluster, proposedSharedPorts, BindingScope.SHARED);
                }
            }

            // if this virtual cluster is binding to <any>, we need to check for conflicts on the <any> interface and *all* specific interfaces,
            // otherwise we just check for conflicts on <any> and the specified specific interface
            var exclusiveCheckSet = virtualCluster.getBindAddress().isEmpty() ? inUseExclusivePorts.keySet() : Set.of(ANY_INTERFACE, virtualCluster.getBindAddress());

            // check the proposed *exclusive ports* for conflicts with the *exclusive ports* seen so far.

            inUseExclusivePorts.entrySet().stream()
                    .filter(interfacePortsEntry -> exclusiveCheckSet.contains(interfacePortsEntry.getKey()))
                    .forEach(entry -> {
                        var bindingInterface = entry.getKey();
                        var ports = entry.getValue();
                        var conflicts = getSortedPortConflicts(ports, proposedExclusivePorts);
                        if (!conflicts.isEmpty()) {
                            throw buildPortConflictException(name, seenVirtualClusters, conflicts,
                                    virtualCluster.getBindAddress(), BindingScope.EXCLUSIVE,
                                    bindingInterface, BindingScope.EXCLUSIVE);
                        }
                    });

            // check the proposed *shared ports* for conflicts with the *exclusive ports* seen so far.

            inUseExclusivePorts.entrySet().stream()
                    .filter(interfacePortsEntry -> exclusiveCheckSet.contains(interfacePortsEntry.getKey()))
                    .forEach((entry) -> {
                        var bindingInterface = entry.getKey();
                        var ports = entry.getValue();
                        var conflicts = getSortedPortConflicts(ports, proposedSharedPorts);
                        if (!conflicts.isEmpty()) {
                            throw buildPortConflictException(name, seenVirtualClusters, conflicts,
                                    virtualCluster.getBindAddress(), BindingScope.SHARED,
                                    bindingInterface, BindingScope.EXCLUSIVE);
                        }
                    });

            // check the proposed *exclusive ports* for conflicts with the *shared ports* seen so far.

            inUseSharedPorts.entrySet().stream()
                    .filter(interfacePortsEntry -> exclusiveCheckSet.contains(interfacePortsEntry.getKey()))
                    .forEach((entry) -> {
                        var bindingInterface = entry.getKey();
                        var ports = entry.getValue().keySet();
                        var conflicts = getSortedPortConflicts(ports, proposedExclusivePorts);
                        if (!conflicts.isEmpty()) {
                            throw buildPortConflictException(name, seenVirtualClusters, conflicts,
                                    virtualCluster.getBindAddress(), BindingScope.EXCLUSIVE,
                                    bindingInterface, BindingScope.SHARED);
                        }
                    });

            // check the proposed *shared ports* for conflicts with the shared ports seen so far on *different* interfaces.

            var sharedCheckSet = new HashSet<>(exclusiveCheckSet);
            sharedCheckSet.remove(virtualCluster.getBindAddress());

            inUseSharedPorts.entrySet().stream()
                    .filter(interfacePortsEntry -> sharedCheckSet.contains(interfacePortsEntry.getKey()))
                    .forEach((entry) -> {
                        var bindingInterface = entry.getKey();
                        var ports = entry.getValue().keySet();
                        var conflicts = getSortedPortConflicts(ports, proposedSharedPorts);
                        if (!conflicts.isEmpty()) {
                            throw buildPortConflictException(name, seenVirtualClusters, conflicts,
                                    virtualCluster.getBindAddress(), BindingScope.SHARED,
                                    bindingInterface, BindingScope.SHARED);
                        }
                    });

            // check for proposed shared ports for differing TLS configuration

            proposedSharedPorts.forEach(p -> {
                var tls = inUseSharedPorts.getOrDefault(virtualCluster.getBindAddress(), Map.of()).get(p);
                if (tls != null && !tls.equals(virtualCluster.isUseTls())) {
                    throw buildOverviewException(name, seenVirtualClusters, buildTlsConflictException(p, virtualCluster.getBindAddress()));
                }
            });

            seenVirtualClusters.add(name);
            inUseExclusivePorts.computeIfAbsent(virtualCluster.getBindAddress(), (k) -> new HashSet<>()).addAll(proposedExclusivePorts);
            var sharedMap = inUseSharedPorts.computeIfAbsent(virtualCluster.getBindAddress(), (k) -> new HashMap<>());
            proposedSharedPorts.forEach(p -> sharedMap.put(p, virtualCluster.isUseTls()));

        });
    }

    private void checkForConflictsWithOtherExclusivePort(HostPort otherHostPort, String name, VirtualCluster cluster, Set<Integer> proposedExclusivePorts,
                                                         BindingScope scope) {
        var ports = Set.of(otherHostPort.port());
        var conflicts = getSortedPortConflicts(ports, proposedExclusivePorts);
        if (!conflicts.isEmpty()) {
            Optional<String> proposedBindingInterface = cluster.getBindAddress();
            var portConflicts = conflicts.stream().map(String::valueOf).collect(Collectors.joining(","));

            throw new IllegalStateException(("The %s bind of port(s) %s for virtual cluster '%s' to %s would conflict with another (non-cluster) port binding").formatted(
                    scope.name().toLowerCase(Locale.ROOT),
                    portConflicts,
                    name,
                    proposedBindingInterface.orElse(ANY_STRING)));
        }
    }

    private List<Integer> getSortedPortConflicts(Set<Integer> ports, Set<Integer> candidates) {
        return ports.stream().filter(candidates::contains).sorted().collect(Collectors.toList());
    }

    private IllegalStateException buildPortConflictException(String virtualClusterName, Set<String> seenVirtualClusters, List<Integer> conflicts,
                                                             Optional<String> proposedBindingInterface, BindingScope proposedScope,
                                                             Optional<String> existingBindingInterface, BindingScope existingBindingScope) {
        var portConflicts = conflicts.stream().map(String::valueOf).collect(Collectors.joining(","));

        var underlying = new IllegalStateException(("The %s bind of port(s) %s to %s would conflict with existing %s port bindings on %s.").formatted(
                proposedScope.name().toLowerCase(Locale.ROOT),
                portConflicts,
                proposedBindingInterface.orElse(ANY_STRING),
                existingBindingScope.name().toLowerCase(Locale.ROOT),
                existingBindingInterface.orElse(ANY_STRING)));
        return buildOverviewException(virtualClusterName, seenVirtualClusters, underlying);
    }

    private IllegalStateException buildTlsConflictException(Integer port, Optional<String> bindingAddress) {
        return new IllegalStateException(
                "The shared bind of port %d to %s has conflicting TLS settings with existing port on the same interface.".formatted(port,
                        bindingAddress.orElse(ANY_STRING)));
    }

    private IllegalStateException buildOverviewException(String virtualClusterName, Set<String> seenVirtualClusters, IllegalStateException underlying) {
        var seenVirtualClustersString = seenVirtualClusters.stream().sorted().map(s -> "'" + s + "'").collect(Collectors.joining(","));
        return new IllegalStateException("Configuration for virtual cluster '%s' conflicts with configuration for virtual cluster%s: %s.".formatted(virtualClusterName,
                seenVirtualClusters.size() > 1 ? "s" : "", seenVirtualClustersString), underlying);
    }

}
