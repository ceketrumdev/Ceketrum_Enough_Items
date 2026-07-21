package net.minecraft.recipe;

import net.minecraft.util.Identifier;

/**
 * Compatibility class for Minecraft 1.20.1 where RecipeEntry did not exist.
 */
public class RecipeEntry<T extends Recipe<?>> {
    private final Identifier id;
    private final T value;

    public RecipeEntry(Identifier id, T value) {
        this.id = id;
        this.value = value;
    }

    public Identifier id() {
        return id;
    }

    public T value() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RecipeEntry)) return false;
        RecipeEntry<?> other = (RecipeEntry<?>) obj;
        return this.id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

