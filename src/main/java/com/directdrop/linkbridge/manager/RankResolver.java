package com.directdrop.linkbridge.manager;

import com.directdrop.linkbridge.hook.LuckPermsHook;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RankResolver {

    private final LuckPermsHook hook;

    public RankResolver(LuckPermsHook hook) {
        this.hook = hook;
    }

    public CompletableFuture<String> resolve(UUID uuid) {
        return hook.getRank(uuid);
    }
}