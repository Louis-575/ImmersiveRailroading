package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.library.TrackModelPart;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class BuilderRadialSwitch extends BuilderSwitch {
	private static final double FLANGE_GAP = 0.15;
	private static final double FROG_PART_OFFSET = 0.5;
	private static final double FROG_CONNECTOR_OVERLAP = 0.5;
	private static final double TOE_LENGTH = 1;
	private static final double TOE_LENGTH_SCALE = 1.1;
	private static final double TOE_INWARD_OFFSET = 0.15;
	private static final double TOE_BACK_OFFSET = 0.5;
	private static final double POINT_LENGTH_SCALE = 1.1;
	private static final double POINT_FROG_OFFSET = 0.5;
	private static final double TURN_SLEEPER_Y_OFFSET = 0.002;
	private static final float CURVE_RAIL_LENGTH_SCALE = 2.25f;

	private final BuilderTurn renderTurnBuilder;
	private final BuilderStraight renderStraightBuilderReal;

	public BuilderRadialSwitch(RailInfo info, World world, Vec3i pos) {
		super(radialSwitchInfo(info), world, pos);

		RailInfo turnInfo = this.info.with(b -> {
			b.settings = b.settings.with(s -> {
				s.type = TrackItems.TURN;
				s.parallelCount = 1;
			});
			b.customInfo = b.placementInfo;
		});

		renderTurnBuilder = (BuilderTurn) turnInfo.getBuilder(world, pos);
		renderStraightBuilderReal = new BuilderStraight(renderStraightInfo(world, pos).withSettings(b -> b.type = TrackItems.STRAIGHT), world, pos, true);
	}

	private static RailInfo radialSwitchInfo(RailInfo info) {
		return info.with(b -> {
			b.customInfo = b.placementInfo;
			b.settings = b.settings.with(s -> s.parallelCount = 1);
		});
	}

	@Override
	protected RailInfo getSwitchStraightInfo(RailInfo info) {
		return projectedStraightInfo(info);
	}

	private RailInfo renderStraightInfo(World world, Vec3i pos) {
		RailInfo straightInfo = projectedStraightInfo(this.info);
		BuilderStraight straightBuilder = new BuilderStraight(straightInfo, world, pos, true);
		return getAdjustedSwitchStraightInfo(straightInfo, straightBuilder, renderTurnBuilder);
	}

	private RailInfo projectedStraightInfo(RailInfo info) {
		return info.withSettings(b -> {
			double radius = Math.max(0, info.settings.length - 1);
			double projectedLength = radius * Math.sin(Math.toRadians(Math.abs(info.settings.degrees)));
			b.length = Math.max(2, (int) Math.ceil(projectedLength) + 1);
		});
	}

	@Override
	protected RailInfo getAdjustedSwitchStraightInfo(RailInfo straightInfo, BuilderStraight straightBuilder, BuilderIterator turnBuilder) {
		return straightInfo.withSettings(b -> {
			double maxOverlap = 0;

			straightBuilder.positions.retainAll(turnBuilder.positions);
			for (Pair<Integer, Integer> straight : straightBuilder.positions) {
				maxOverlap = Math.max(maxOverlap, new Vec3d(straight.getKey(), 0, straight.getValue()).length());
			}

			maxOverlap *= 1.2;
			b.length = Math.max(b.length, (int) Math.ceil(maxOverlap) + 3);
		});
	}

	@Override
	public List<VecYPR> getRenderData() {
		RadialSwitchLayout layout = getLayout();
		List<VecYPR> data = new ArrayList<>();

		double renderStep = layout.renderStep;
		addSegments(data, layout.straightPath, 0, layout.straightEnd, renderStep, TrackModelPart.RAIL_BASE);
		addSegments(data, layout.turnPath, 0, layout.turnEnd, renderStep, TrackModelPart.RAIL_BASE, TURN_SLEEPER_Y_OFFSET * info.settings.gauge.scale(), false);

		addSegments(data, layout.straightPath, 0, layout.straightEnd, renderStep, layout.straightStockRail);
		addMovableClosureSegments(data, layout.straightPath, layout.toeEndDistance, layout.heelDistance, renderStep, layout.straightFrogRail, layout.straightClosureThrow);
		addSegmentsExcept(data, layout.straightPath, layout.heelDistance, layout.straightEnd, renderStep, layout.straightGapStartDistance, layout.straightPointDistance, layout.straightFrogRail);
		addFrogConnector(data, layout.straightPath, layout.straightGapStartDistance, layout.straightWingStartDistance, layout.straightFrogRail);
		addSegments(data, layout.turnPath, 0, layout.turnEnd, renderStep, layout.turnStockRail, 0, true);
		addMovableClosureSegments(data, layout.turnPath, layout.toeEndDistance, layout.heelDistance, renderStep, layout.turnFrogRail, layout.curvedClosureThrow);
		addSegmentsExcept(data, layout.turnPath, layout.heelDistance, layout.turnEnd, renderStep, layout.curvedGapStartDistance, layout.curvedPointDistance, layout.turnFrogRail, true);
		addFrogConnector(data, layout.turnPath, layout.curvedGapStartDistance, layout.curvedWingStartDistance, layout.turnFrogRail);

		data.addAll(getSwitchPartRenderData(layout));
		return data;
	}

	@Override
	protected void buildTracks() {
		straightBuilder.buildTracks();

		TileRail parent = world.getBlockEntity(straightBuilder.getParentPos(), TileRail.class);
		if (parent != null) {
			parent.info = this.info.offset(this.pos.subtract(parent.getPos()));
			parent.markDirty();
		}

		turnBuilder.buildTracks();
	}

	private RadialSwitchLayout getLayout() {
		double scale = info.settings.gauge.scale();
		double renderStep = scale * info.getTrackModel().spacing;
		double stepSize = Math.max(0.05, renderStep / 4);
		List<VecYPR> straightPath = getFullPath(renderStraightBuilderReal, stepSize);
		List<VecYPR> turnPath = getFullPath(renderTurnBuilder, stepSize);

		TrackModelPart straightFrogRail = info.placementInfo.direction == TrackDirection.RIGHT ? TrackModelPart.RAIL_RIGHT : TrackModelPart.RAIL_LEFT;
		TrackModelPart turnFrogRail = info.placementInfo.direction == TrackDirection.RIGHT ? TrackModelPart.RAIL_LEFT : TrackModelPart.RAIL_RIGHT;
		TrackModelPart straightStockRail = otherRail(straightFrogRail);
		TrackModelPart turnStockRail = otherRail(turnFrogRail);

		double heelDistance = findSeparationDistance(straightPath, turnPath, 0.5 * scale);
		FrogGeometry frog = calculateFrogGeometry(straightPath, turnPath, heelDistance, straightFrogRail, turnFrogRail);

		double stretcherDistance = Math.min(heelDistance * 0.25, 0.75 * scale);

		return new RadialSwitchLayout(
				straightPath,
				turnPath,
				renderStep,
				totalDistance(straightPath),
				totalDistance(turnPath),
				heelDistance,
				stretcherDistance,
				straightFrogRail,
				straightStockRail,
				turnFrogRail,
				turnStockRail,
				frog
		);
	}

	private List<VecYPR> getSwitchPartRenderData(RadialSwitchLayout layout) {
		List<VecYPR> data = new ArrayList<>();
		addToePieces(data, layout);
		data.add(switchPart(applyLinearThrow(pointAtDistance(layout.straightPath, layout.stretcherDistance), layout.stretcherDistance, layout.heelDistance, layout.stretcherThrow), TrackModelPart.STRETCHER_BAR));

		VecYPR straightHeel = pointAtDistance(layout.straightPath, layout.heelDistance);
		VecYPR turnHeel = pointAtDistance(layout.turnPath, layout.heelDistance);
		data.add(switchPart(VecUtil.between(straightHeel, turnHeel), turnHeel.getYaw(), 0, TrackModelPart.HEEL_BLOCK));

		double pointOffset = POINT_FROG_OFFSET * info.settings.gauge.scale();
		data.add(pointPart(pointAtDistance(layout.turnPath, Math.max(layout.curvedGapStartDistance, layout.curvedPointDistance - pointOffset)), TrackModelPart.POINT_LEFT));
		data.add(pointPart(pointAtDistance(layout.straightPath, Math.max(layout.straightGapStartDistance, layout.straightPointDistance - pointOffset)), TrackModelPart.POINT_RIGHT));
		data.add(switchPart(pointAtDistance(layout.turnPath, layout.curvedWingStartDistance), TrackModelPart.WING_RAIL_LEFT));
		data.add(switchPart(pointAtDistance(layout.straightPath, layout.straightWingStartDistance), TrackModelPart.WING_RAIL_RIGHT));
		data.add(switchPart(pointAtDistance(layout.straightPath, layout.straightCheckDistance), TrackModelPart.CHECK_RAIL_LEFT));
		data.add(switchPart(pointAtDistance(layout.turnPath, layout.curvedCheckDistance), TrackModelPart.CHECK_RAIL_RIGHT));
		return data;
	}

	private void addToePieces(List<VecYPR> data, RadialSwitchLayout layout) {
		boolean rightHand = info.placementInfo.direction == TrackDirection.RIGHT;
		List<VecYPR> leftRailPath = rightHand ? layout.turnPath : layout.straightPath;
		List<VecYPR> rightRailPath = rightHand ? layout.straightPath : layout.turnPath;
		double leftThrow = rightHand ? layout.curvedClosureThrow : layout.straightClosureThrow;
		double rightThrow = rightHand ? layout.straightClosureThrow : layout.curvedClosureThrow;

		data.add(toePart(applyToeOffset(applyLinearThrow(pointAtDistance(leftRailPath, 0), 0, layout.heelDistance, leftThrow), true), TrackModelPart.TOE_LEFT));
		data.add(toePart(applyToeOffset(applyLinearThrow(pointAtDistance(rightRailPath, 0), 0, layout.heelDistance, rightThrow), false), TrackModelPart.TOE_RIGHT));
	}

	private void addRail(List<VecYPR> data, List<VecYPR> path, double from, double to, TrackModelPart rail) {
		addRail(data, path, from, to, rail, 0, false);
	}

	private void addRail(List<VecYPR> data, List<VecYPR> path, double from, double to, TrackModelPart rail, double yOffset, boolean scaleCurveRails) {
		VecYPR span = span(path, from, to, rail, yOffset, scaleCurveRails);
		if (span != null) {
			data.add(span);
		}
	}

	private void addSegments(List<VecYPR> data, List<VecYPR> path, double from, double to, double maxStep, TrackModelPart part) {
		addSegments(data, path, from, to, maxStep, part, 0, false);
	}

	private void addSegments(List<VecYPR> data, List<VecYPR> path, double from, double to, double maxStep, TrackModelPart part, double yOffset, boolean scaleCurveRails) {
		double length = to - from;
		if (length <= 0) {
			return;
		}

		int count = Math.max(1, (int) Math.ceil(length / maxStep));
		for (int i = 0; i < count; i++) {
			double start = from + length * i / count;
			double end = from + length * (i + 1) / count;
			addRail(data, path, start, end, part, yOffset, scaleCurveRails);
		}
	}

	private void addSegmentsExcept(List<VecYPR> data, List<VecYPR> path, double from, double to, double maxStep, double gapStart, double gapEnd, TrackModelPart part) {
		addSegmentsExcept(data, path, from, to, maxStep, gapStart, gapEnd, part, false);
	}

	private void addSegmentsExcept(List<VecYPR> data, List<VecYPR> path, double from, double to, double maxStep, double gapStart, double gapEnd, TrackModelPart part, boolean scaleCurveRails) {
		double length = to - from;
		if (length <= 0) {
			return;
		}

		if (gapEnd <= gapStart) {
			addSegments(data, path, from, to, maxStep, part, 0, scaleCurveRails);
			return;
		}

		int count = Math.max(1, (int) Math.ceil(length / maxStep));
		for (int i = 0; i < count; i++) {
			double start = from + length * i / count;
			double end = from + length * (i + 1) / count;
			addRail(data, path, start, Math.min(end, gapStart), part, 0, scaleCurveRails);
			addRail(data, path, Math.max(start, gapEnd), end, part, 0, scaleCurveRails);
		}
	}

	private void addMovableClosureSegments(List<VecYPR> data, List<VecYPR> path, double from, double to, double maxStep, TrackModelPart part, double throwDistance) {
		double length = to - from;
		if (length <= 0) {
			return;
		}

		int count = Math.max(1, (int) Math.ceil(length / maxStep));
		for (int i = 0; i < count; i++) {
			double start = from + length * i / count;
			double end = from + length * (i + 1) / count;
			VecYPR span = movableClosureSpan(path, start, end, to, part, throwDistance);
			if (span != null) {
				data.add(span);
			}
		}
	}

	private void addFrogConnector(List<VecYPR> data, List<VecYPR> path, double hiddenFrom, double wingAt, TrackModelPart part) {
		double total = totalDistance(path);
		double overlap = FROG_CONNECTOR_OVERLAP * info.settings.gauge.scale();
		double from = Math.max(0, Math.min(hiddenFrom, wingAt) - overlap);
		double to = Math.min(total, Math.max(hiddenFrom, wingAt) + overlap);
		if (wingAt <= hiddenFrom) {
			to = Math.min(to, hiddenFrom);
		} else {
			from = Math.max(from, hiddenFrom);
		}

		VecYPR start = pointAtDistance(path, from);
		VecYPR end = pointAtDistance(path, to);
		double length = horizontalDistance(start, end);
		if (length <= 0.001) {
			return;
		}

		float yaw = VecUtil.toYaw(end.subtract(start));
		data.add(new VecYPR(start.x, start.y, start.z, yaw, start.getPitch(), (float) (length / info.getTrackModel().spacing * 1.005), part));
	}

	private VecYPR movableClosureSpan(List<VecYPR> path, double from, double to, double heelDistance, TrackModelPart part, double throwDistance) {
		double total = totalDistance(path);
		from = Math.max(0, Math.min(total, from));
		to = Math.max(0, Math.min(total, to));
		if (to <= from) {
			return null;
		}

		VecYPR start = applyLinearThrow(pointAtDistance(path, from), from, heelDistance, throwDistance);
		VecYPR end = applyLinearThrow(pointAtDistance(path, to), to, heelDistance, throwDistance);
		float yaw = VecUtil.toYaw(end.subtract(start));
		float length = (float) (horizontalDistance(start, end) / info.getTrackModel().spacing * 1.005);
		return new VecYPR(start.x, start.y, start.z, yaw, 0, length, part);
	}

	private VecYPR span(List<VecYPR> path, double from, double to, TrackModelPart part, double yOffset, boolean scaleCurveRails) {
		double total = totalDistance(path);
		from = Math.max(0, Math.min(total, from));
		to = Math.max(0, Math.min(total, to));
		if (to <= from) {
			return null;
		}

		VecYPR start = pointAtDistance(path, from);
		float length = (float) ((to - from) / info.getTrackModel().spacing * 1.005);
		if (scaleCurveRails && (part == TrackModelPart.RAIL_LEFT || part == TrackModelPart.RAIL_RIGHT)) {
			float angle = angleDelta(pointAtDistance(path, from).getYaw(), pointAtDistance(path, to).getYaw());
			float railScale = part == TrackModelPart.RAIL_LEFT ? 1 - CURVE_RAIL_LENGTH_SCALE * angle / 180 : 1 + CURVE_RAIL_LENGTH_SCALE * angle / 180;
			length *= Math.max(0.05f, railScale);
		}
		return new VecYPR(start.x, start.y + yOffset, start.z, start.getYaw(), 0, length, part);
	}

	private FrogGeometry calculateFrogGeometry(List<VecYPR> straightPath, List<VecYPR> turnPath, double heelDistance, TrackModelPart straightFrogRail, TrackModelPart turnFrogRail) {
		double scale = info.settings.gauge.scale();
		double straightTotal = totalDistance(straightPath);
		double turnTotal = totalDistance(turnPath);
		FrogIntersection intersection = calculateAnalyticFrog(straightPath, turnPath, straightFrogRail, turnFrogRail, scale);
		double gapBack = FLANGE_GAP * scale / Math.max(0.05, intersection.sinTheta);
		double partOffset = FROG_PART_OFFSET * scale;

		return new FrogGeometry(
				intersection.straightDistance,
				intersection.curvedDistance,
				Math.max(heelDistance, intersection.straightDistance - gapBack),
				Math.max(heelDistance, intersection.curvedDistance - gapBack),
				Math.min(straightTotal, Math.max(heelDistance, intersection.straightDistance + partOffset)),
				Math.min(turnTotal, Math.max(heelDistance, intersection.curvedDistance + partOffset))
		);
	}

	private FrogIntersection calculateAnalyticFrog(List<VecYPR> straightPath, List<VecYPR> turnPath, TrackModelPart straightFrogRail, TrackModelPart turnFrogRail, double scale) {
		if (straightPath.isEmpty() || turnPath.isEmpty()) {
			return new FrogIntersection(0, 0, 1);
		}

		double radius = Math.max(1, info.settings.length - 1);
		double railOffset = 0.755 * scale;
		VecYPR origin = straightPath.get(0);
		Vec3d right = VecUtil.fromYaw(1, origin.getYaw() + 90);
		double hand = localRight(origin, turnPath.get(turnPath.size() - 1), right) >= 0 ? 1 : -1;
		double straightRailOffset = railOffsetFor(straightFrogRail, railOffset);
		double curvedRailOffset = railOffsetFor(turnFrogRail, railOffset);
		double centerRight = hand * radius;
		double curvedRadius = Math.max(0.1, radius - hand * curvedRailOffset);
		double deltaRight = straightRailOffset - centerRight;
		double discriminant = curvedRadius * curvedRadius - deltaRight * deltaRight;

		if (discriminant < 0) {
			double fallbackDistance = findSeparationDistance(straightPath, turnPath, 1.5 * scale);
			return new FrogIntersection(fallbackDistance, fallbackDistance, 1);
		}

		double straightDistance = Math.sqrt(discriminant);
		double angle = Math.atan2(straightDistance, Math.abs(deltaRight));
		double curvedDistance = angle * curvedRadius;
		double maxAngle = Math.toRadians(Math.max(1, Math.abs(info.settings.degrees)));
		double maxStraight = radius * Math.sin(maxAngle);
		straightDistance = clamp(straightDistance, 0, Math.max(totalDistance(straightPath), maxStraight));
		curvedDistance = clamp(curvedDistance, 0, totalDistance(turnPath));
		return new FrogIntersection(straightDistance, curvedDistance, Math.sin(angle));
	}

	private double railOffsetFor(TrackModelPart rail, double railOffset) {
		return rail == TrackModelPart.RAIL_LEFT ? railOffset : -railOffset;
	}

	private double localRight(Vec3d origin, Vec3d point, Vec3d right) {
		Vec3d delta = point.subtract(origin);
		return delta.x * right.x + delta.z * right.z;
	}

	private VecYPR applyToeOffset(VecYPR point, boolean leftToe) {
		double scale = info.settings.gauge.scale();
		double inwardOffset = TOE_INWARD_OFFSET * scale * (leftToe ? -1 : 1);
		Vec3d offset = VecUtil.fromYaw(inwardOffset, point.getYaw() + 90)
				.add(VecUtil.fromYaw(-TOE_BACK_OFFSET * scale, point.getYaw()));
		return new VecYPR(point.add(offset), point.getYaw(), point.getPitch());
	}

	private double[] cumulativeDistances(List<VecYPR> path) {
		double[] distances = new double[path.size()];
		for (int i = 1; i < path.size(); i++) {
			distances[i] = distances[i - 1] + horizontalDistance(path.get(i - 1), path.get(i));
		}
		return distances;
	}

	private double findSeparationDistance(List<VecYPR> straightPath, List<VecYPR> turnPath, double target) {
		int maxIndex = Math.min(straightPath.size(), turnPath.size()) - 1;
		double straightDistance = 0;
		double turnDistance = 0;
		double previousSeparation = horizontalDistance(straightPath.get(0), turnPath.get(0));
		double previousDistance = 0;

		for (int i = 1; i <= maxIndex; i++) {
			straightDistance += horizontalDistance(straightPath.get(i - 1), straightPath.get(i));
			turnDistance += horizontalDistance(turnPath.get(i - 1), turnPath.get(i));
			double distance = (straightDistance + turnDistance) / 2;
			double separation = horizontalDistance(straightPath.get(i), turnPath.get(i));
			if (separation >= target) {
				double range = separation - previousSeparation;
				double position = range == 0 ? 0 : (target - previousSeparation) / range;
				return previousDistance + (distance - previousDistance) * Math.max(0, Math.min(1, position));
			}
			previousSeparation = separation;
			previousDistance = distance;
		}
		return previousDistance;
	}

	private VecYPR pointAtDistance(List<VecYPR> path, double distance) {
		if (path.isEmpty()) {
			return new VecYPR(Vec3d.ZERO, info.placementInfo.yaw);
		}
		if (distance <= 0) {
			return path.get(0);
		}

		double remaining = distance;
		for (int i = 1; i < path.size(); i++) {
			VecYPR previous = path.get(i - 1);
			VecYPR next = path.get(i);
			double segment = horizontalDistance(previous, next);
			if (remaining <= segment) {
				double position = segment == 0 ? 0 : remaining / segment;
				Vec3d point = previous.add(next.subtract(previous).scale(position));
				return new VecYPR(point, lerpAngle(previous.getYaw(), next.getYaw(), position), 0);
			}
			remaining -= segment;
		}

		return path.get(path.size() - 1);
	}

	private float lerpAngle(float start, float end, double position) {
		float delta = end - start;
		while (delta > 180) {
			delta -= 360;
		}
		while (delta < -180) {
			delta += 360;
		}
		return (float) (start + delta * position);
	}

	private float angleDelta(float start, float end) {
		float delta = start - end;
		while (delta > 180) {
			delta -= 360;
		}
		while (delta < -180) {
			delta += 360;
		}
		return delta;
	}

	private VecYPR applyLinearThrow(VecYPR point, double distance, double heelDistance, double throwDistance) {
		if (throwDistance == 0 || heelDistance <= 0 || distance >= heelDistance) {
			return point;
		}

		double switchOffset = 1 - (distance / heelDistance);
		Vec3d offset = VecUtil.fromYaw(throwDistance * switchOffset, point.getYaw() + 90);
		return new VecYPR(point.add(offset), point.getYaw(), point.getPitch());
	}

	private VecYPR switchPart(VecYPR point, TrackModelPart... parts) {
		return switchPart(point, point.getYaw(), point.getPitch(), parts);
	}

	private VecYPR toePart(VecYPR point, TrackModelPart part) {
		float length = (float) (TOE_LENGTH_SCALE * info.settings.gauge.scale());
		return new VecYPR(point, point.getYaw() + 180, point.getPitch(), length, part);
	}

	private VecYPR pointPart(VecYPR point, TrackModelPart part) {
		float length = (float) (POINT_LENGTH_SCALE * info.settings.gauge.scale());
		return new VecYPR(point, point.getYaw() + 180, point.getPitch(), length, part);
	}

	private List<VecYPR> getFullPath(BuilderCubicCurve builder, double stepSize) {
		List<BuilderBase> subBuilders = builder.getSubBuilders();
		if (subBuilders == null || subBuilders.isEmpty()) {
			return builder.getPath(stepSize);
		}

		List<VecYPR> path = new ArrayList<>();
		for (BuilderBase subBuilder : subBuilders) {
			if (!(subBuilder instanceof IIterableTrack)) {
				continue;
			}
			Vec3d offset = new Vec3d(subBuilder.pos.subtract(builder.pos))
					.add(subBuilder.info.placementInfo.placementPosition)
					.subtract(builder.info.placementInfo.placementPosition);
			for (VecYPR point : ((IIterableTrack) subBuilder).getPath(stepSize)) {
				VecYPR shifted = new VecYPR(point.add(offset), point.getYaw(), point.getPitch());
				if (!path.isEmpty() && horizontalDistance(path.get(path.size() - 1), shifted) < 0.001) {
					continue;
				}
				path.add(shifted);
			}
		}
		return path;
	}

	private VecYPR switchPart(Vec3d point, float yaw, float pitch, TrackModelPart... parts) {
		return new VecYPR(point, yaw + 180, pitch, parts);
	}

	private TrackModelPart otherRail(TrackModelPart rail) {
		return rail == TrackModelPart.RAIL_LEFT ? TrackModelPart.RAIL_RIGHT : TrackModelPart.RAIL_LEFT;
	}

	private Vec3d railPoint(VecYPR point, TrackModelPart rail, double scale) {
		double modelRailOffset = 0.755 * scale;
		double offset = rail == TrackModelPart.RAIL_LEFT ? modelRailOffset : -modelRailOffset;
		return point.add(VecUtil.fromYaw(offset, point.getYaw() + 90));
	}

	private double horizontalDistance(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private double totalDistance(List<VecYPR> path) {
		double distance = 0;
		for (int i = 1; i < path.size(); i++) {
			distance += horizontalDistance(path.get(i - 1), path.get(i));
		}
		return distance;
	}

	private double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static class FrogIntersection {
		private final double straightDistance;
		private final double curvedDistance;
		private final double sinTheta;

		private FrogIntersection(double straightDistance, double curvedDistance, double sinTheta) {
			this.straightDistance = straightDistance;
			this.curvedDistance = curvedDistance;
			this.sinTheta = sinTheta;
		}
	}

	private class RadialSwitchLayout {
		private final List<VecYPR> straightPath;
		private final List<VecYPR> turnPath;
		private final double renderStep;
		private final double straightEnd;
		private final double turnEnd;
		private final double heelDistance;
		private final double toeEndDistance;
		private final double stretcherDistance;
		private final TrackModelPart straightFrogRail;
		private final TrackModelPart straightStockRail;
		private final TrackModelPart turnFrogRail;
		private final TrackModelPart turnStockRail;
		private final double straightGapStartDistance;
		private final double curvedGapStartDistance;
		private final double straightWingStartDistance;
		private final double curvedWingStartDistance;
		private final double straightCheckDistance;
		private final double curvedCheckDistance;
		private final double straightIntersectionDistance;
		private final double curvedIntersectionDistance;
		private final double straightPointDistance;
		private final double curvedPointDistance;
		private final double straightClosureThrow;
		private final double curvedClosureThrow;
		private final double stretcherThrow;

		private RadialSwitchLayout(List<VecYPR> straightPath, List<VecYPR> turnPath, double renderStep, double straightEnd, double turnEnd, double heelDistance, double stretcherDistance, TrackModelPart straightFrogRail, TrackModelPart straightStockRail, TrackModelPart turnFrogRail, TrackModelPart turnStockRail, FrogGeometry frog) {
			this.straightPath = straightPath;
			this.turnPath = turnPath;
			this.renderStep = renderStep;
			this.straightEnd = straightEnd;
			this.turnEnd = turnEnd;
			this.heelDistance = heelDistance;
			this.toeEndDistance = Math.min(heelDistance, TOE_LENGTH * info.settings.gauge.scale());
			this.stretcherDistance = stretcherDistance;
			this.straightFrogRail = straightFrogRail;
			this.straightStockRail = straightStockRail;
			this.turnFrogRail = turnFrogRail;
			this.turnStockRail = turnStockRail;
			double wingRailLength = renderStep;
			double frogPartOffset = FROG_PART_OFFSET * info.settings.gauge.scale();
			this.straightGapStartDistance = frog.straightWingDistance;
			this.curvedGapStartDistance = frog.curvedWingDistance;
			this.straightWingStartDistance = Math.min(straightEnd, Math.max(heelDistance, frog.straightWingDistance - wingRailLength + frogPartOffset));
			this.curvedWingStartDistance = Math.min(turnEnd, Math.max(heelDistance, frog.curvedWingDistance - wingRailLength + frogPartOffset));
			this.straightCheckDistance = Math.min(straightEnd, straightWingStartDistance);
			this.curvedCheckDistance = Math.min(turnEnd, curvedWingStartDistance);
			this.straightIntersectionDistance = frog.straightIntersectionDistance;
			this.curvedIntersectionDistance = frog.curvedIntersectionDistance;
			this.straightPointDistance = frog.straightPointDistance;
			this.curvedPointDistance = frog.curvedPointDistance;

			double throwDirection = info.placementInfo.direction == TrackDirection.RIGHT ? 1 : -1;
			double throwDistance = FLANGE_GAP * info.settings.gauge.scale() * throwDirection;
			boolean thrown = info.switchState == SwitchState.TURN;
			this.straightClosureThrow = thrown ? -throwDistance : 0;
			this.curvedClosureThrow = thrown ? 0 : throwDistance;
			this.stretcherThrow = thrown ? -throwDistance : throwDistance;
		}
	}

	private static class FrogGeometry {
		private final double straightIntersectionDistance;
		private final double curvedIntersectionDistance;
		private final double straightWingDistance;
		private final double curvedWingDistance;
		private final double straightPointDistance;
		private final double curvedPointDistance;

		private FrogGeometry(double straightIntersectionDistance, double curvedIntersectionDistance, double straightWingDistance, double curvedWingDistance, double straightPointDistance, double curvedPointDistance) {
			this.straightIntersectionDistance = straightIntersectionDistance;
			this.curvedIntersectionDistance = curvedIntersectionDistance;
			this.straightWingDistance = straightWingDistance;
			this.curvedWingDistance = curvedWingDistance;
			this.straightPointDistance = straightPointDistance;
			this.curvedPointDistance = curvedPointDistance;
		}
	}
}
