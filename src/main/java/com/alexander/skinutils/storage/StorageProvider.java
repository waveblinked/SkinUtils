package com.alexander.skinutils.storage;

import com.alexander.skinutils.skin.SkinData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageProvider {

    void init();

    void shutdown();

    void save(UUID uuid, SkinData skin);

    Optional<SkinData> load(UUID uuid);

    void remove(UUID uuid);

    void saveHistory(UUID uuid, SkinData skin, String source);

    List<SkinHistory> getHistory(UUID uuid, int limit);

    record SkinHistory(SkinData skin, String source, long timestamp) {}
}
