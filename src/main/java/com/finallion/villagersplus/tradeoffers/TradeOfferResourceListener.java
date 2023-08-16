package com.finallion.villagersplus.tradeoffers;

import com.finallion.villagersplus.VillagersPlus;
import com.google.gson.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.Registry;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TradeOfferResourceListener extends JsonDataLoader implements IdentifiableResourceReloadListener {
    private static Set<String> datapacks = new HashSet<>();

    public TradeOfferResourceListener() {
        super(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().setLenient().create(), "villager_trades");
        datapacks.clear();
    }

    @Override
    public Identifier getFabricId() {
        return new Identifier(VillagersPlus.MOD_ID,"villager_data_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> loader, ResourceManager manager, Profiler profiler) {
        for (ResourcePack pack : manager.streamResourcePacks().collect(Collectors.toList())) {
            if (datapacks.contains(pack.getName())) continue;

            datapacks.add(pack.getName());
            try {
                for (VillagerProfession villager : Registry.VILLAGER_PROFESSION.stream().collect(Collectors.toList())) {
                    if (villager.id().contains("none")) continue;

                    loadAndMergeTradeOffers(pack, villager);
                }
            } catch (IOException e) {
                VillagersPlus.LOGGER.info("Deserializing datapack: " + pack.getName() + " throws IOException.");

                e.printStackTrace();
            }
        }
    }

    private void loadAndMergeTradeOffers(ResourcePack pack, VillagerProfession villager) throws IOException {
        String id = villager.id();
        if (id.contains(":")) id = id.split(":")[1];

        InputStream tradeInputStreamSupplier = null;
        try {
            tradeInputStreamSupplier = pack.open(ResourceType.SERVER_DATA, new Identifier(VillagersPlus.MOD_ID, "villager_trades/" + id + ".json"));
        } catch (IOException e) {
            VillagersPlus.LOGGER.debug("Villager " + villager + " not found in datapack " + pack.getName() + ". Maybe missing VillagersPlus identifier!");
        }

        if (tradeInputStreamSupplier == null) {
            return;
        }

        VillagersPlus.LOGGER.info("Deserializing datapack added trades of profession: " + villager + " from " + pack.getName());

        JsonObject tradeData = new Gson().fromJson(new InputStreamReader(tradeInputStreamSupplier), JsonObject.class);

        TradeOfferManager.deserializeJson(tradeData);

        // trades from datapacks
        Map<VillagerProfession, Int2ObjectOpenHashMap<TradeOffers.Factory[]>> datapackTrades = TradeOfferRegistryLoader.getRegistryForLoading();

        // merge trades added from datapack with the default trades added via data-json
        TradeOffers.PROFESSION_TO_LEVELED_TRADE.forEach((profession, defaultProfessionTrades) -> {
            Int2ObjectOpenHashMap<TradeOffers.Factory[]> datapackProfessionTrades = datapackTrades.getOrDefault(profession, new Int2ObjectOpenHashMap<>());

            datapackProfessionTrades.forEach((level, datapackLevelTrades) -> { // collect trades per level and per profession from the datapack
                TradeOffers.Factory[] defaultLevelTrades = defaultProfessionTrades.get(level);

                TradeOffers.Factory[] mergedTrades = Stream.concat( // if default trades are available, merge them with the datapack trades
                                (defaultLevelTrades != null) ? Arrays.stream(defaultLevelTrades) : Stream.empty(),
                                Arrays.stream(datapackLevelTrades))
                        .distinct()
                        .toArray(TradeOffers.Factory[]::new);

                defaultProfessionTrades.put(level, mergedTrades);
            });
        });
    }

}