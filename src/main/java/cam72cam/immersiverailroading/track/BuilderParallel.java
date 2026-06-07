package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.util.PlacementInfo;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BuilderParallel extends BuilderBase {
	private final List<BuilderBase> subBuilders = new ArrayList<>();

	public static boolean supports(TrackItems type) {
		switch (type) {
			case STRAIGHT:
			case TURN:
			case SWITCH:
			case CUSTOM:
				return true;
			default:
				return false;
		}
	}

	public static boolean supports(RailInfo info) {
		return supports(info.settings.type);
	}

	public BuilderParallel(RailInfo info, World world, Vec3i pos) {
		super(info, world, pos);

		for (int i = 0; i < info.settings.parallelCount; i++) {
			final int trackIndex = i;
			boolean hasCustomCurveEndpoint = info.settings.type == TrackItems.CUSTOM || info.settings.type == TrackItems.SWITCH;
			boolean isDefaultCustom = hasCustomCurveEndpoint // For default custom curve/switch when there is no endpoint
					&& info.customInfo.placementPosition.equals(info.placementInfo.placementPosition);
			Vec3d offset = VecUtil.fromYaw(trackIndex * info.settings.parallelGap, info.placementInfo.yaw + 90);
			Vec3d customOffset = hasCustomCurveEndpoint && !isDefaultCustom
					? VecUtil.fromYaw(trackIndex * info.settings.parallelGap * -1, info.customInfo.yaw + 90)
					: offset;
			Vec3d placement = info.placementInfo.placementPosition.add(offset);
			Vec3i childPosOffset = new Vec3i(placement.x, 0, placement.z);
			Vec3i childPos = pos.add(childPosOffset);
			RailInfo childInfo = info.with(b -> {
				b.settings = b.settings.with(settings -> {
					settings.parallelCount = 1;
					if (settings.type == TrackItems.TURN) {
						double radiusOffset = trackIndex * info.settings.parallelGap * (info.placementInfo.direction == TrackDirection.RIGHT ? -1 : 1);
						settings.length = Math.max(2, (int) Math.round(info.settings.length + radiusOffset));
					}
				});
				b.placementInfo = offset(b.placementInfo, offset, childPosOffset);
				b.customInfo = b.customInfo != null ? offset(b.customInfo, customOffset, childPosOffset) : null;
			});
			BuilderBase childBuilder = childInfo.getBuilder(world, childPos);
			if (i == 0) {
				this.setParentPos(childBuilder.getParentPos().subtract(pos));
			}
			subBuilders.add(childBuilder);
		}
	}

	private PlacementInfo offset(PlacementInfo info, Vec3d offset, Vec3i childPosOffset) {
		return new PlacementInfo(
				info.placementPosition.add(offset).subtract(childPosOffset),
				info.direction,
				info.yaw,
				info.control != null ? info.control.add(offset).subtract(childPosOffset) : null
		);
	}

	@Override
	public List<VecYPR> getRenderData() {
		List<VecYPR> data = new ArrayList<>();
		for (BuilderBase subBuilder : subBuilders) {
			Vec3d offset = new Vec3d(subBuilder.pos.subtract(pos)).add(subBuilder.info.placementInfo.placementPosition).subtract(info.placementInfo.placementPosition);
			subBuilder.getRenderData().stream().map(rd -> rd.add(offset)).forEach(data::add);
		}
		return data;
	}

	@Override
	public boolean canBuild() {
		return subBuilders.stream().allMatch(BuilderBase::canBuild);
	}

	@Override
	public void build() {
		subBuilders.forEach(BuilderBase::build);
	}

	@Override
	public void clearArea() {
		subBuilders.forEach(BuilderBase::clearArea);
	}

	@Override
	public List<TrackBase> getTracksForRender() {
		return subBuilders.stream().map(BuilderBase::getTracksForRender).flatMap(List::stream).collect(Collectors.toList());
	}

	@Override
	public List<TrackBase> getTracksForFloating() {
		return Collections.emptyList();
	}

	@Override
	public int costTies() {
		return subBuilders.stream().mapToInt(BuilderBase::costTies).sum();
	}

	@Override
	public int costRails() {
		return subBuilders.stream().mapToInt(BuilderBase::costRails).sum();
	}

	@Override
	public int costBed() {
		return subBuilders.stream().mapToInt(BuilderBase::costBed).sum();
	}

	@Override
	public int costFill() {
		return subBuilders.stream().mapToInt(BuilderBase::costFill).sum();
	}

	@Override
	public void setDrops(List<ItemStack> drops) {
		if (!subBuilders.isEmpty()) {
			subBuilders.get(0).setDrops(drops);
		}
	}
}
