package client.paint.mc;

import client.paint.core.basefinder.BlockSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Block classification for Base Finder, driven by block registry names so it needs few Minecraft symbols.
 * "Player placed" means "does not generate naturally as a block" - villages, mineshafts, ruined portals and other
 * generated structures still contain such blocks, which is why Base Finder treats them as clues, never proof.
 * VERIFY (26.3): getMinY()/getMaxY() names, ResourceLocation/Identifier#getPath.
 */
final class McBlocks implements BlockSource {
    private static final Set<String> PLAYER_EXACT = Set.of("crafting_table", "furnace", "blast_furnace", "smoker", "chest", "trapped_chest",
            "barrel", "torch", "wall_torch", "soul_torch", "lantern", "ladder", "glass", "glass_pane", "bookshelf", "anvil", "enchanting_table",
            "bricks", "stone_bricks", "hopper", "dropper", "dispenser", "piston", "sticky_piston", "observer", "repeater", "comparator",
            "redstone_lamp", "tnt", "bed", "beacon", "smooth_stone", "polished_andesite", "polished_diorite", "polished_granite",
            "smooth_quartz", "quartz_block", "quartz_bricks", "nether_bricks", "red_nether_bricks", "iron_block", "gold_block", "diamond_block",
            "netherite_block", "emerald_block", "lapis_block", "redstone_block", "cobblestone_stairs", "cobblestone_slab");
    private static final String[] PLAYER_SUFFIX = {"_planks", "_wool", "_concrete", "_concrete_powder", "_stained_glass", "_stained_glass_pane",
            "_door", "_trapdoor", "_fence", "_fence_gate", "_sign", "_wall_sign", "_slab", "_stairs", "_shulker_box", "_bed", "_carpet",
            "_glazed_terracotta"};
    private static final String[] NATURAL_SUFFIX = {"_ore", "stone", "deepslate", "dirt", "sand", "gravel", "netherrack", "andesite", "diorite",
            "granite", "tuff", "calcite", "bedrock", "terracotta", "sandstone", "basalt", "blackstone", "end_stone", "grass_block", "podzol",
            "mycelium", "clay", "snow_block", "ice", "obsidian", "magma_block", "soul_soil", "dripstone_block", "moss_block", "mud",
            "cobblestone", "mossy_cobblestone", "_log", "_leaves", "coal_block", "copper_block", "amethyst_block", "budding_amethyst"};

    private final Map<Block, Kind> cache = new IdentityHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private ClientLevel level() { return Minecraft.getInstance().level; }

    @Override public boolean isChunkLoaded(int cx, int cz) {
        ClientLevel l = level();
        return l != null && l.hasChunk(cx, cz);
    }
    @Override public int minY() { return level().getMinY(); }                 // VERIFY name
    @Override public int maxY() { return level().getMaxY() + 1; }             // VERIFY name (getMaxY is inclusive)

    @Override public Kind kind(int x, int y, int z) {
        BlockState s = level().getBlockState(pos.set(x, y, z));
        if (s.isAir()) return Kind.AIR;
        return cache.computeIfAbsent(s.getBlock(), McBlocks::classify);
    }

    static Kind classify(Block b) {
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();               // VERIFY: Identifier#getPath
        switch (id) {
            case "amethyst_cluster": case "large_amethyst_bud": case "medium_amethyst_bud": case "small_amethyst_bud": return Kind.AMETHYST;
            case "kelp": case "kelp_plant": return Kind.KELP;
            case "vine": return Kind.VINE;
            case "water": return Kind.WATER;
            case "ancient_debris": return Kind.ORE_DEBRIS;
            case "nether_portal": return Kind.PORTAL;
            default: break;
        }
        if (id.endsWith("_ore")) {
            if (id.contains("diamond")) return Kind.ORE_DIAMOND;
            if (id.contains("emerald")) return Kind.ORE_EMERALD;
            if (id.contains("gold")) return Kind.ORE_GOLD;
            if (id.contains("iron")) return Kind.ORE_IRON;
            return Kind.ORE_OTHER;   // coal, copper, lapis, redstone, quartz
        }
        if (PLAYER_EXACT.contains(id)) return Kind.PLAYER_PLACED;
        for (String s : PLAYER_SUFFIX) if (id.endsWith(s)) return Kind.PLAYER_PLACED;
        for (String s : NATURAL_SUFFIX) if (id.equals(s) || id.endsWith(s)) return Kind.SOLID_NATURAL;
        return Kind.OTHER;
    }
}
