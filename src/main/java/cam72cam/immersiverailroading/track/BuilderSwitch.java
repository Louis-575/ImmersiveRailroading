package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class BuilderSwitch extends BuilderBase implements IIterableTrack {

	protected BuilderIterator turnBuilder;
	protected BuilderStraight straightBuilder;
	protected BuilderStraight realStraightBuilder;
	protected final BuilderStraight straightBuilderReal;

	public BuilderSwitch(RailInfo info, World world, Vec3i pos) {
		super(info, world, pos);
		
		RailInfo turnInfo = getSwitchTurnInfo(info);
		RailInfo straightInfo = getSwitchStraightInfo(info);

		{
			turnBuilder = (BuilderIterator) turnInfo.getBuilder(world, pos);
			straightBuilder = new BuilderStraight(straightInfo, world, pos, true);
			realStraightBuilder = new BuilderStraight(straightInfo, world, pos, true);

			straightInfo = getAdjustedSwitchStraightInfo(straightInfo, straightBuilder, turnBuilder);
		}
		

		straightBuilder = new BuilderStraight(straightInfo, world, pos, true);
		straightBuilderReal = new BuilderStraight(straightInfo.withSettings(b -> b.type = TrackItems.STRAIGHT), world, pos, true);
		
		turnBuilder.overrideFlexible = true;
		
		for(TrackBase turn : turnBuilder.tracks) {
			if (turn instanceof TrackRail) {
				turn.overrideParent(straightBuilder.getParentPos());
			}
		}
		for (TrackBase straight : straightBuilder.tracks) {
			if (straight instanceof TrackGag) {
				straight.setFlexible();
			}
		}
	}

	protected RailInfo getSwitchTurnInfo(RailInfo info) {
		return info.withSettings(b -> b.type = info.customInfo.placementPosition.equals(info.placementInfo.placementPosition) ? TrackItems.TURN : TrackItems.CUSTOM);
	}

	protected RailInfo getSwitchStraightInfo(RailInfo info) {
		return info;
	}

	protected RailInfo getAdjustedSwitchStraightInfo(RailInfo straightInfo, BuilderStraight straightBuilder, BuilderIterator turnBuilder) {
		return straightInfo.withSettings(b -> {
			double maxOverlap = 0;

			straightBuilder.positions.retainAll(turnBuilder.positions);

			for (Pair<Integer, Integer> straight : straightBuilder.positions) {
				maxOverlap = Math.max(maxOverlap, new Vec3d(straight.getKey(), 0, straight.getValue()).length());
			}

			maxOverlap *= 1.2;
			b.length = (int) Math.ceil(maxOverlap) + 3;
		});
	}

	@Override
	public List<BuilderBase> getSubBuilders() {
		List<BuilderBase> subTurns = turnBuilder.getSubBuilders();
		List<BuilderBase> subStraights = straightBuilderReal.getSubBuilders();

		if (subTurns == null && subStraights == null) {
			return null;
		}

		List<BuilderBase> res = new ArrayList<>();
		if (subTurns == null) {
			res.add(turnBuilder);
		} else {
			res.addAll(subTurns);
		}
		if (subStraights == null) {
			res.add(straightBuilderReal);
		} else {
			res.addAll(subStraights);
		}
		return res;
	}

	@Override
	public int costTies() {
		return straightBuilder.costTies() + turnBuilder.costTies();
	}
	
	@Override
	public int costRails() {
		return straightBuilder.costRails() + turnBuilder.costRails();
	}

	@Override
	public int costBed() {
		return straightBuilder.costBed() + turnBuilder.costBed();
	}
	
	@Override
	public int costFill() {
		return super.costFill();
	}
	
	@Override
	public void setDrops(List<ItemStack> drops) {
		straightBuilder.setDrops(drops);
	}
	

	@Override
	public boolean canBuild() {
		return straightBuilder.canBuild() && turnBuilder.canBuild();
	}
	
	@Override
	public void build() {
		super.build();
	}

	@Override
	protected void buildTracks() {
		straightBuilder.buildTracks();
		turnBuilder.buildTracks();
	}
	
	@Override
	public void clearArea() {
		straightBuilder.clearArea();
		turnBuilder.clearArea();
	}
	
	@Override
	public List<TrackBase> getTracksForRender() {
		List<TrackBase> data = straightBuilder.getTracksForRender();
		data.addAll(turnBuilder.getTracksForRender());
		return data;
	}

	@Override
	public List<TrackBase> getTracksForBuild() {
		List<TrackBase> data = new ArrayList<>();
		data.addAll(straightBuilder.getTracksForBuild());
		data.addAll(turnBuilder.getTracksForBuild());
		return data;
	}

	@Override
	public List<VecYPR> getRenderData() {
		List<VecYPR> data = straightBuilder.getRenderData();
		data.addAll(turnBuilder.getRenderData());
		return data;
	}

	@Override
	public List<VecYPR> getPath(double stepSize) {
		return realStraightBuilder.getPath(stepSize);
	}
}
