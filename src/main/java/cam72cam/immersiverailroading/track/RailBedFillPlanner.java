package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class RailBedFillPlanner {
    private static final int DISTANCE_SCALE = 10;
    private static final int[][] NEIGHBORS = {
            {1, 0, 10}, {-1, 0, 10}, {0, 1, 10}, {0, -1, 10},
            {1, 1, 14}, {1, -1, 14}, {-1, 1, 14}, {-1, -1, 14}
    };

    private final World world;
    private final RailSettings settings;
    private final List<TrackBase> tracks;

    public RailBedFillPlanner(World world, RailSettings settings, List<TrackBase> tracks) {
        this.world = world;
        this.settings = settings;
        this.tracks = tracks;
    }

    public List<Vec3i> plan() {
        List<Vec3i> planned = new ArrayList<>();
        for (Vec3i pos : surface()) {
            if (BlockUtil.canBeReplaced(world, pos, false)) {
                planned.add(pos);
            }
        }
        planned.sort(Comparator.comparingInt(pos -> pos.y));
        return planned;
    }

    public Set<Vec3i> surface() {
        Set<Vec3i> blocks = new HashSet<>();
        if (settings.railBedFill.isEmpty()) {
            return blocks;
        }

        Map<Pair<Integer, Integer>, Integer> footprint = new HashMap<>();
        for (TrackBase track : tracks) {
            Vec3i pos = track.getPos().down();
            Pair<Integer, Integer> cell = Pair.of(pos.x, pos.z);
            Integer existing = footprint.get(cell);
            if (existing == null || pos.y > existing) {
                footprint.put(cell, pos.y);
            }
        }

        int width = Math.max(0, Math.min(10, settings.railBedFillWidth));
        int maxDistance = width * DISTANCE_SCALE;
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(node -> node.distance));
        Map<Pair<Integer, Integer>, Integer> bestDistance = new HashMap<>();
        Map<Pair<Integer, Integer>, Integer> bestSourceY = new HashMap<>();

        for (Map.Entry<Pair<Integer, Integer>, Integer> entry : footprint.entrySet()) {
            Node node = new Node(entry.getKey().getLeft(), entry.getKey().getRight(), entry.getValue(), 0);
            queue.add(node);
            bestDistance.put(entry.getKey(), 0);
            bestSourceY.put(entry.getKey(), entry.getValue());
        }

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            Pair<Integer, Integer> cell = Pair.of(node.x, node.z);
            if (node.distance != bestDistance.get(cell) || node.sourceY != bestSourceY.get(cell)) {
                continue;
            }

            blocks.add(new Vec3i(node.x, node.sourceY, node.z));

            for (int[] neighbor : NEIGHBORS) {
                int nextDistance = node.distance + neighbor[2];
                if (nextDistance > maxDistance) {
                    continue;
                }

                Pair<Integer, Integer> nextCell = Pair.of(node.x + neighbor[0], node.z + neighbor[1]);
                Integer existingDistance = bestDistance.get(nextCell);
                Integer existingSourceY = bestSourceY.get(nextCell);
                if (existingDistance != null && (existingDistance < nextDistance || existingDistance == nextDistance && existingSourceY >= node.sourceY)) {
                    continue;
                }

                bestDistance.put(nextCell, nextDistance);
                bestSourceY.put(nextCell, node.sourceY);
                queue.add(new Node(nextCell.getLeft(), nextCell.getRight(), node.sourceY, nextDistance));
            }
        }

        return blocks;
    }

    private static class Node {
        private final int x;
        private final int z;
        private final int sourceY;
        private final int distance;

        private Node(int x, int z, int sourceY, int distance) {
            this.x = x;
            this.z = z;
            this.sourceY = sourceY;
            this.distance = distance;
        }
    }
}
