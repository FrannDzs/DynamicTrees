package com.ferreusveritas.dynamictrees.util.json;

import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.api.registry.Registry;
import com.ferreusveritas.dynamictrees.api.registry.RegistryEntry;
import com.google.gson.JsonElement;
import net.minecraft.util.ResourceLocation;

/**
 * Gets {@link RegistryEntry} object of type {@link T} from the given {@link Registry} object.
 *
 * @author Harley O'Connor
 */
public final class RegistryEntryGetter<T extends RegistryEntry<T>> implements IJsonObjectGetter<T> {

    private final Registry<T> registry;
    private final String registryDisplayName;

    public RegistryEntryGetter(Registry<T> registry, String registryDisplayName) {
        this.registry = registry;
        this.registryDisplayName = registryDisplayName;
    }

    @Override
    public ObjectFetchResult<T> get(JsonElement jsonElement) {
        final ObjectFetchResult<ResourceLocation> resourceLocationFetchResult = JsonObjectGetters.RESOURCE_LOCATION_GETTER.get(jsonElement);

        if (!resourceLocationFetchResult.wasSuccessful())
            return ObjectFetchResult.failure(resourceLocationFetchResult.getErrorMessage());

        // Get and process registry name, so that if the namespace is 'minecraft' we instead default to 'dynamictrees'.
        final ResourceLocation registryName = TreeRegistry.processResLoc(resourceLocationFetchResult.getValue());

        if (!this.registry.has(registryName))
            return ObjectFetchResult.failure("Json element referenced unregistered " + this.registryDisplayName + " '" + registryName + "'.");

        return ObjectFetchResult.success(this.registry.get(registryName));
    }

}
