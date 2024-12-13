package com.magmaguy.freeminecraftmodels.customentity.core;

import com.magmaguy.freeminecraftmodels.MetadataHandler;
import one.tranic.irs.PluginSchedulerBuilder;
import one.tranic.irs.task.TaskImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class SkeletonWatchers implements Listener {
    private final Skeleton skeleton;
    private final Set<UUID> viewers = new CopyOnWriteArraySet<>();
    private TaskImpl tick;

    public SkeletonWatchers(Skeleton skeleton) {
        this.skeleton = skeleton;
        tick();
    }

    public void remove() {
        tick.cancel();
    }

    private void tick() {
        tick = PluginSchedulerBuilder.builder(MetadataHandler.PLUGIN)
                .delayTicks(1)
                .period(1)
                .task(() -> updateWatcherList())
                .async()
                .run();
    }

    private void updateWatcherList() {
        List<UUID> newPlayers = new ArrayList<>();
        for (Player player : skeleton.getCurrentLocation().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(skeleton.getCurrentLocation()) < Math.pow(2, Bukkit.getSimulationDistance() * 16D)) {
                newPlayers.add(player.getUniqueId());
                if (!viewers.contains(player.getUniqueId())) {
                    displayTo(player);
                }
            }
        }

        List<UUID> toRemove = new ArrayList<>();
        for (UUID viewer : viewers) {
            if (!newPlayers.contains(viewer)) {
                toRemove.add(viewer);
            }
        }
        viewers.removeAll(toRemove);
        toRemove.forEach(this::hideFrom);
    }

    private void displayTo(Player player) {
        viewers.add(player.getUniqueId());
        skeleton.getBones().forEach(bone -> bone.displayTo(player));
    }

    private void hideFrom(UUID uuid) {
        viewers.remove(uuid);
        skeleton.getBones().forEach(bone -> bone.hideFrom(uuid));
    }

    public void reset() {
        Set<UUID> tempViewers = Collections.synchronizedSet(new HashSet<>(viewers));
        tempViewers.forEach(viewer -> {
            hideFrom(viewer);
            displayTo(Bukkit.getPlayer(viewer));
        });
    }

    public void sendPackets(Bone bone) {
        if (viewers.isEmpty()) return;
        bone.sendUpdatePacket();
    }
}
