package io.quarkus.datasource.deployment.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.util.ArtifactInfoUtil;

/**
 * A build item that represents the "quarkus.datasource.db-kind" value.
 * This is generated by specific extensions that are meant to take away the burden of
 * configuring anything datasource related from the user.
 */
public final class DefaultDataSourceDbKindBuildItem extends MultiBuildItem {

    public static final String TEST = "test";
    private final String dbKind;
    private final Class<?> callerClass;
    private volatile String scope;

    public DefaultDataSourceDbKindBuildItem(String dbKind) {
        this.dbKind = dbKind;
        String callerClassName = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()
                .getCanonicalName();
        try {
            callerClass = Thread.currentThread().getContextClassLoader().loadClass(callerClassName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public String getDbKind() {
        return dbKind;
    }

    public String getScope(CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (scope == null) {
            Map.Entry<String, String> artifact = ArtifactInfoUtil.groupIdAndArtifactId(callerClass, curateOutcomeBuildItem);
            for (AppDependency i : curateOutcomeBuildItem.getEffectiveModel().getFullDeploymentDeps()) {
                if (i.getArtifact().getArtifactId().equals(artifact.getValue())
                        && i.getArtifact().getGroupId().equals(artifact.getKey())) {
                    scope = i.getScope();
                    break;
                }
            }
            if (scope == null) {
                throw new RuntimeException("Could not determine scope for " + dbKind);
            }
        }
        return scope;
    }

    public static Optional<String> resolve(Optional<String> configured,
            List<DefaultDataSourceDbKindBuildItem> defaultDbKinds,
            boolean enableImplicitResolution,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (configured.isPresent()) {
            return Optional.of(DatabaseKind.normalize(configured.get()));
        }

        if (!enableImplicitResolution) {
            return Optional.empty();
        }

        return resolveImplicitDbKind(defaultDbKinds, curateOutcomeBuildItem);
    }

    /**
     * Attempts to resolve the implicit DB kind for the case where none has been specified.
     */
    private static Optional<String> resolveImplicitDbKind(List<DefaultDataSourceDbKindBuildItem> defaultDbKinds,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (defaultDbKinds.isEmpty()) {
            return Optional.empty();
        } else if (defaultDbKinds.size() == 1) {
            return Optional.of(defaultDbKinds.get(0).dbKind);
        } else {
            //if we have one and only one test scoped driver we assume it is the default
            //if is commmon to use a different DB such as H2 in tests
            DefaultDataSourceDbKindBuildItem testScopedDriver = null;
            for (DefaultDataSourceDbKindBuildItem i : defaultDbKinds) {
                if (i.getScope(curateOutcomeBuildItem).equals(TEST)) {
                    if (testScopedDriver == null) {
                        testScopedDriver = i;
                    } else {
                        return Optional.empty();
                    }
                }
            }
            if (testScopedDriver == null) {
                return Optional.empty();
            } else {
                return Optional.of(testScopedDriver.dbKind);
            }
        }
    }
}