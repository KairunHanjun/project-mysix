package me.simonhamour.mysix.testing;

/** Headless worlds have no tag packs; bind empty tags on each item holder explicitly. */
public final class ItemTagBootstrap {
    private ItemTagBootstrap() {}
    public static void refresh() {
        try {
            var method = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
            method.setAccessible(true);
            for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM.listElements().toList()) {
                method.invoke(holder, java.util.List.of());
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
