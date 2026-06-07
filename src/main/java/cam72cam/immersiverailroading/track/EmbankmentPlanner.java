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

public class EmbankmentPlanner {
    private static final int DISTANCE_SCALE = 10;
    private static final int[][] NEIGHBORS = {
            {1, 0, 10}, {-1, 0, 10}, {0, 1, 10}, {0, -1, 10},
            {1, 1, 14}, {1, -1, 14}, {-1, 1, 14}, {-1, -1, 14}
    };

    private final World world;
    private final RailSettings settings;
    private final List<TrackBase> tracks;

    public EmbankmentPlanner(World world, RailSettings settings, List<TrackBase> tracks) {
        this.world = world;
        this.settings = settings;
        this.tracks = tracks;
    }

    public List<Vec3i> plan() {
        if (settings.embankment.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Pair<Integer, Integer>, Integer> footprint = new HashMap<>();
        Set<Vec3i> railBedSurface = new HashSet<>();
        for (TrackBase track : tracks) {
            Vec3i pos = track.getPos().down();
            Pair<Integer, Integer> cell = Pair.of(pos.x, pos.z);
            Integer existing = footprint.get(cell);
            if (existing == null || pos.y > existing) {
                footprint.put(cell, pos.y);
            }
            if (!settings.railBedFill.isEmpty()) {
                railBedSurface.add(pos);
            }
        }

        if (footprint.isEmpty()) {
            return new ArrayList<>();
        }

        int offset = Math.max(0, Math.min(10, settings.embankmentOffset));
        int height = Math.max(1, Math.min(40, settings.embankmentHeight));
        float gradient = Math.max(0.1f, settings.embankmentGradient);
        int maxDistance = (int) Math.ceil((offset + (height - 1) / gradient) * DISTANCE_SCALE);

        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(node -> node.distance));
        Map<Pair<Integer, Integer>, Integer> bestDistance = new HashMap<>();
        Map<Pair<Integer, Integer>, Integer> bestSourceY = new HashMap<>();
        for (Map.Entry<Pair<Integer, Integer>, Integer> entry : footprint.entrySet()) {
            Node node = new Node(entry.getKey().getLeft(), entry.getKey().getRight(), entry.getValue(), 0);
            queue.add(node);
            bestDistance.put(entry.getKey(), 0);
            bestSourceY.put(entry.getKey(), entry.getValue());
        }

        Set<Vec3i> blocks = new HashSet<>();
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            Pair<Integer, Integer> cell = Pair.of(node.x, node.z);
            if (node.distance != bestDistance.get(cell) || node.sourceY != bestSourceY.get(cell)) {
                continue;
            }

            addColumn(blocks, railBedSurface, node, offset, height, gradient);

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

        List<Vec3i> planned = new ArrayList<>(blocks);
        planned.sort(Comparator.comparingInt(pos -> pos.y));
        return planned;
    }

    private void addColumn(Set<Vec3i> blocks, Set<Vec3i> railBedSurface, Node node, int offset, int height, float gradient) {
        double horizontalDistance = node.distance / (double) DISTANCE_SCALE;
        int drop = horizontalDistance <= offset ? 0 : (int) Math.ceil((horizontalDistance - offset) * gradient - 0.0001);
        if (drop >= height) {
            return;
        }

        int topY = node.sourceY - drop;
        int bottomY = node.sourceY - height + 1;
        for (int y = topY; y >= bottomY; y--) {
            Vec3i pos = new Vec3i(node.x, y, node.z);
            if (railBedSurface.contains(pos)) {
                continue;
            }
            if (!BlockUtil.canBeReplaced(world, pos, false)) {
                break;
            }
            blocks.add(pos);
        }
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
