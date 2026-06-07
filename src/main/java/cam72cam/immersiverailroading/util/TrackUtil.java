package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.track.BuilderBase;
import cam72cam.immersiverailroading.track.VecYPR;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;

import java.util.List;

public class TrackUtil {
    public static PlacementInfo getNeighborNode(Player player, World world, Vec3i pos, Vec3d hit, ItemStack stack) {
        RailSettings stackInfo = RailSettings.from(stack);
        Vec3d worldPos = new Vec3d(pos).add(hit);
        Vec3d minPos = worldPos;
        double min = Double.MAX_VALUE;
        int hori = Math.max((int) (stackInfo.gauge.scale() * 2), 1);
        int vert = 1;
        float yaw = player.getRotationYaw();
        float rotationYawHead = (player.getRotationYawHead() + 360f) % 360f;

        for (int x = -hori; x <= hori; x++) {
            for (int y = -vert; y <= vert; y++) {
                for (int z = -hori; z <= hori; z++) {
                    Vec3i offset = pos.add(x, y, z);
                    TileRailBase tile = world.getBlockEntity(offset, TileRailBase.class);
                    while (tile != null){
                        if (!(tile instanceof TileRail)) {
                            tile = tile.getParentTile();
                        }

                        TileRail rail = (TileRail) tile;
                        if (rail == null || rail.info == null ||
                                Math.abs(rail.getTrackGauge() - stackInfo.gauge.value()) > 1.0E-6) continue;

                        BuilderBase builder = rail.info.getBuilder(world);
                        List<VecYPR> renderData = builder.getRenderData();
                        if (renderData.isEmpty()) continue;

                        if (renderData.size() > 1) {
                            Vec3d p1 = renderData.get(0).add(rail.info.placementInfo.placementPosition).add(
                                    tile.getPos());
                            float yaw1 = VecUtil.toYaw(renderData.get(1).subtract(renderData.get(0)));
                            double dist1 = p1.distanceTo(worldPos);
                            if (dist1 < min) {
                                min = dist1;
                                minPos = p1;
                                yaw = yaw1;
                            }

                            Vec3d p2 = renderData.get(renderData.size() - 1)
                                                 .add(rail.info.placementInfo.placementPosition).add(tile.getPos());
                            float yaw2 = VecUtil.toYaw(renderData.get(renderData.size() - 2).subtract(
                                    renderData.get(renderData.size() - 1)));
                            double dist = p2.distanceTo(worldPos);
                            if (dist < min) {
                                min = dist;
                                minPos = p2;
                                yaw = yaw2;
                            }
                        } else {
                            Vec3d p = renderData.get(0).add(rail.info.placementInfo.placementPosition).add(
                                    tile.getPos());
                            float currentYaw = renderData.get(0).getYaw();
                            if (Math.abs(currentYaw - rotationYawHead) > 90) {
                                currentYaw += 180;
                            }
                            double dist = p.distanceTo(worldPos);
                            if (dist < min) {
                                min = dist;
                                minPos = p;
                                yaw = currentYaw;
                            }
                        }

                        tile = tile.getReplacedTile();
                    }
                }
            }
        }

        if (min <= hori) {
            yaw = Config.ConfigDebug.trackSnapAngle ? (540 - yaw) % 360 : rotationYawHead;
            return new PlacementInfo(minPos, TrackDirection.NONE, yaw, null);
        }
        return null;
    }
}
