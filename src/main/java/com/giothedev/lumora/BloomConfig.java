package com.giothedev.lumora;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class BloomConfig {
    public static class BloomData {
        public float r, g, b;
        public float intensity;
        public boolean occludes = false;
        public ActivationData activation;

        public static class ActivationData {
            public String type = "always"; // always, property, redstone_power
            public String key;
            public int min = 1;
        }

        public BloomData(float r, float g, float b, float intensity) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.intensity = intensity;
        }
    }

    public static final Map<ResourceLocation, BloomData> BLOOM_BLOCKS = new HashMap<>();
    public static final Map<ResourceLocation, BloomData> BLOOM_PARTICLES = new HashMap<>();

    public static void load(ResourceManager manager) {
        BLOOM_BLOCKS.clear();
        BLOOM_PARTICLES.clear();

        manager.listResources("bloom_config", id->id.getPath().endsWith(".json"))
                .forEach((id, resource)->{
                    try(var reader = new InputStreamReader(resource.open())){
                        JsonObject json = GsonHelper.parse(reader);

                        if(json.has("blocks")){
                            parseArray(json.getAsJsonArray("blocks"), BLOOM_BLOCKS);
                        }

                        if(json.has("particles")){
                            parseArray(json.getAsJsonArray("particles"), BLOOM_PARTICLES);
                        }
                    }catch(Exception e){
                        Lumora.LOGGER.error("Failed to load bloom config {}: {}", id, e.getMessage());
                    }
                });

        Lumora.LOGGER.info("Bloom config loaded: {} blocks, {} particles", BLOOM_BLOCKS.size(), BLOOM_PARTICLES.size());
    }

    // PARSER
    private static void parseArray(JsonArray array, Map<ResourceLocation, BloomData> map) {
        for(JsonElement e : array){
            try{
                ResourceLocation id;
                float r = 1f, g = 1f, b = 1f;
                float intensity = 1f;
                BloomData data = new BloomData(r, g, b, intensity);

                // String format example: "minecraft:glowstone"
                if(e.isJsonPrimitive()){
                    id = ResourceLocation.parse(e.getAsString());
                }

                // {object, id, intensity, etc}
                else if(e.isJsonObject()){
                    JsonObject obj = e.getAsJsonObject();

                    if(!obj.has("id")){
                        Lumora.LOGGER.warn("Skipping bloom entry without 'id': {}", obj);
                        continue;
                    }

                    id = ResourceLocation.parse(obj.get("id").getAsString());

                    if(obj.has("intensity")){
                        data.intensity = obj.get("intensity").getAsFloat();
                    }

                    if(obj.has("color")){
                        JsonArray col = obj.getAsJsonArray("color");

                        if(col.size() >= 3){
                            data.r = col.get(0).getAsFloat();
                            data.g = col.get(1).getAsFloat();
                            data.b = col.get(2).getAsFloat();
                        }
                    }

                    if(obj.has("activation")){
                        JsonObject act = obj.getAsJsonObject("activation");

                        BloomData.ActivationData a = new BloomData.ActivationData();

                        if(act.has("type")){
                            a.type = act.get("type").getAsString();
                        }
                        if(act.has("key")){
                            a.key = act.get("key").getAsString();
                        }
                        if(act.has("min")) {
                            a.min = act.get("min").getAsInt();
                        }
                        data.activation = a;
                    }
                }else{
                    Lumora.LOGGER.warn("Invalid bloom entry: {}", e);
                    continue;
                }

                map.put(id, data);

            }catch (Exception ex){
                Lumora.LOGGER.warn("Failed to parse bloom entry: {}", e);
            }
        }
    }
}