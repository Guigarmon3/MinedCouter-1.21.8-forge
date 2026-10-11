package dev.blockcounter;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Block Counter: compact HUD with blocks mined (world total + this session).
 * Client-side only mod (see clientSideOnly in mods.toml).
 */
@Mod(BlockCounterMod.MODID)
public final class BlockCounterMod {
    public static final String MODID = "blockcounter";

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(MODID, "block_counter");

    public BlockCounterMod(FMLJavaModLoadingContext context) {
        // EventBus 7 (Forge 1.21.8): mod bus events are obtained through the BusGroup.
        AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(BlockCounterMod::onAddLayers);
    }

    private static void onAddLayers(AddGuiOverlayLayersEvent event) {
        // Sits just below the F3 debug screen, inside the "post sleep" stack,
        // so it hides with F1 (hideGui) and F3 draws over it.
        event.getLayeredDraw().addBelow(
                ForgeLayeredDraw.POST_SLEEP_STACK,
                LAYER_ID,
                ForgeLayeredDraw.DEBUG_OVERLAY,
                BlockCounterHud::render);
    }
}
