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
	private static final double FROG_OFFSET_BACK = 0.3;
	private static final double FLANGE_GAP = 0.15;
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
		addSegmentsExcept(data, layout.straightPath, 0, layout.straightEnd, renderStep, layout.straightGapStartDistance, layout.straightPointDistance, layout.straightFrogRail);
		addSegments(data, layout.turnPath, 0, layout.turnEnd, renderStep, layout.turnStockRail, 0, true);
		addSegmentsExcept(data, layout.turnPath, 0, layout.turnEnd, renderStep, layout.curvedGapStartDistance, layout.curvedPointDistance, layout.turnFrogRail, true);

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
		double fallbackFrogDistance = findSeparationDistance(straightPath, turnPath, 1.5 * scale) + renderStep * 0.5;
		FrogGeometry frog = calculateFrogGeometry(straightPath, turnPath, heelDistance, fallbackFrogDistance, straightFrogRail, turnFrogRail);

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
		data.add(switchPart(pointAtDistance(layout.straightPath, 0), TrackModelPart.TOE_LEFT, TrackModelPart.TOE_RIGHT));
		data.add(switchPart(applyPointThrow(pointAtDistance(layout.straightPath, layout.stretcherDistance), layout.stretcherDistance, layout.heelDistance), TrackModelPart.STRETCHER_BAR));

		VecYPR straightHeel = pointAtDistance(layout.straightPath, layout.heelDistance);
		VecYPR turnHeel = pointAtDistance(layout.turnPath, layout.heelDistance);
		data.add(switchPart(VecUtil.between(straightHeel, turnHeel), turnHeel.getYaw(), 0, TrackModelPart.HEEL_BLOCK));

		data.add(switchPart(pointAtDistance(layout.turnPath, layout.curvedPointDistance), TrackModelPart.POINT_LEFT));
		data.add(switchPart(pointAtDistance(layout.straightPath, layout.straightPointDistance), TrackModelPart.POINT_RIGHT));
		data.add(switchPart(pointAtDistance(layout.turnPath, layout.curvedWingStartDistance), TrackModelPart.WING_RAIL_LEFT));
		data.add(switchPart(pointAtDistance(layout.straightPath, layout.straightWingStartDistance), TrackModelPart.WING_RAIL_RIGHT));
		data.add(switchPart(pointAtDistance(layout.straightPath, layout.straightCheckDistance), TrackModelPart.CHECK_RAIL_LEFT));
		data.add(switchPart(pointAtDistance(layout.turnPath, layout.curvedCheckDistance), TrackModelPart.CHECK_RAIL_RIGHT));
		return data;
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

	private FrogGeometry calculateFrogGeometry(List<VecYPR> straightPath, List<VecYPR> turnPath, double heelDistance, double fallbackFrogDistance, TrackModelPart straightFrogRail, TrackModelPart turnFrogRail) {
		double scale = info.settings.gauge.scale();
		double renderStep = scale * info.getTrackModel().spacing;
		double boundsTolerance = Math.max(scale, renderStep * 1.5);
		double straightTotal = totalDistance(straightPath);
		double turnTotal = totalDistance(turnPath);
		FrogIntersection intersection = findFrogIntersection(
				straightPath,
				turnPath,
				heelDistance,
				fallbackFrogDistance,
				boundsTolerance,
				straightFrogRail,
				turnFrogRail,
				scale
		);

		if (intersection != null) {
			double gapHalf = (FLANGE_GAP * scale / intersection.sinTheta) / 2;
			return offsetFrogGeometry(new FrogGeometry(
					intersection.straightDistance,
					intersection.curvedDistance,
					Math.max(heelDistance, intersection.straightDistance - gapHalf),
					Math.max(heelDistance, intersection.curvedDistance - gapHalf),
					Math.min(straightTotal, intersection.straightDistance + gapHalf),
					Math.min(turnTotal, intersection.curvedDistance + gapHalf)
			), heelDistance, straightTotal, turnTotal, FROG_OFFSET_BACK * scale);
		}

		double fallbackGapHalf = FLANGE_GAP * scale / 2;
		double straightDistance = Math.max(heelDistance, fallbackFrogDistance);
		double curvedDistance = fallbackFrogDistance;
		return offsetFrogGeometry(new FrogGeometry(
				straightDistance,
				curvedDistance,
				Math.max(heelDistance, straightDistance - fallbackGapHalf),
				Math.max(heelDistance, curvedDistance - fallbackGapHalf),
				Math.min(straightTotal, straightDistance + fallbackGapHalf),
				Math.min(turnTotal, curvedDistance + fallbackGapHalf)
		), heelDistance, straightTotal, turnTotal, FROG_OFFSET_BACK * scale);
	}

	private FrogGeometry offsetFrogGeometry(FrogGeometry frog, double heelDistance, double straightTotal, double turnTotal, double offset) {
		return new FrogGeometry(
				Math.max(heelDistance, frog.straightIntersectionDistance - offset),
				Math.max(heelDistance, frog.curvedIntersectionDistance - offset),
				Math.max(heelDistance, frog.straightWingDistance - offset),
				Math.max(heelDistance, frog.curvedWingDistance - offset),
				Math.min(straightTotal, Math.max(heelDistance, frog.straightPointDistance - offset)),
				Math.min(turnTotal, Math.max(heelDistance, frog.curvedPointDistance - offset))
		);
	}

	private FrogIntersection findFrogIntersection(List<VecYPR> straightPath, List<VecYPR> turnPath, double heelDistance, double fallbackFrogDistance, double boundsTolerance, TrackModelPart straightFrogRail, TrackModelPart turnFrogRail, double scale) {
		double[] straightDistances = cumulativeDistances(straightPath);
		double[] turnDistances = cumulativeDistances(turnPath);
		FrogIntersection best = null;
		double bestScore = Double.MAX_VALUE;

		for (int straightIndex = 1; straightIndex < straightPath.size(); straightIndex++) {
			if (straightDistances[straightIndex] < heelDistance) {
				continue;
			}

			Vec3d straightRailStart = railPoint(straightPath.get(straightIndex - 1), straightFrogRail, scale);
			Vec3d straightRailEnd = railPoint(straightPath.get(straightIndex), straightFrogRail, scale);
			double straightRailLength = horizontalDistance(straightRailStart, straightRailEnd);
			double straightPathLength = straightDistances[straightIndex] - straightDistances[straightIndex - 1];
			if (straightRailLength <= 0 || straightPathLength <= 0) {
				continue;
			}
			Vec3d straightDirection = normalize(straightRailEnd.subtract(straightRailStart));

			for (int turnIndex = 1; turnIndex < turnPath.size(); turnIndex++) {
				if (turnDistances[turnIndex] < heelDistance) {
					continue;
				}

				Vec3d curveRailStart = railPoint(turnPath.get(turnIndex - 1), turnFrogRail, scale);
				Vec3d curveRailEnd = railPoint(turnPath.get(turnIndex), turnFrogRail, scale);
				double curveRailLength = horizontalDistance(curveRailStart, curveRailEnd);
				double curvePathLength = turnDistances[turnIndex] - turnDistances[turnIndex - 1];
				if (curveRailLength <= 0 || curvePathLength <= 0) {
					continue;
				}
				Vec3d curveDirection = normalize(curveRailEnd.subtract(curveRailStart));
				double sinTheta = Math.abs(cross(straightDirection, curveDirection));
				if (sinTheta < 0.05) {
					continue;
				}

				double[] intersection = lineIntersection(straightRailStart, straightDirection, curveRailStart, curveDirection);
				if (intersection == null) {
					continue;
				}

				double straightRailDistance = clamp(intersection[0], 0, straightRailLength);
				double curveRailDistance = clamp(intersection[1], 0, curveRailLength);
				Vec3d straightPoint = straightRailStart.add(straightDirection.scale(straightRailDistance));
				Vec3d curvePoint = curveRailStart.add(curveDirection.scale(curveRailDistance));
				double missDistance = horizontalDistance(straightPoint, curvePoint);
				if (missDistance > boundsTolerance) {
					continue;
				}

				double straightDistance = straightDistances[straightIndex - 1] + straightPathLength * (straightRailDistance / straightRailLength);
				double curvedDistance = turnDistances[turnIndex - 1] + curvePathLength * (curveRailDistance / curveRailLength);
				if (straightDistance < heelDistance || curvedDistance < heelDistance) {
					continue;
				}

				double fallbackScore = Math.abs(straightDistance - fallbackFrogDistance) + Math.abs(curvedDistance - fallbackFrogDistance);
				double score = missDistance * 1000 + fallbackScore;
				if (score < bestScore) {
					best = new FrogIntersection(straightDistance, curvedDistance, sinTheta);
					bestScore = score;
				}
			}
		}
		return best;
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

	private VecYPR applyPointThrow(VecYPR point, double distance, double heelDistance) {
		if (info.switchState != SwitchState.TURN || heelDistance <= 0 || distance >= heelDistance) {
			return point;
		}

		double switchOffset = 1 - (distance / heelDistance);
		double dist = 0.2 * switchOffset * info.settings.gauge.scale() * info.getTrackModel().spacing;
		Vec3d offset = VecUtil.fromYaw(dist, point.getYaw() + 90 + info.placementInfo.direction.toYaw());
		double offsetAngle = Math.toDegrees(0.2 / Math.max(1, heelDistance / info.settings.gauge.scale()));
		if (info.placementInfo.direction == TrackDirection.RIGHT) {
			offsetAngle = -offsetAngle;
		}

		return new VecYPR(point.add(offset), point.getYaw() + (float) offsetAngle, 0);
	}

	private VecYPR switchPart(VecYPR point, TrackModelPart... parts) {
		return switchPart(point, point.getYaw(), point.getPitch(), parts);
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

	private Vec3d normalize(Vec3d vector) {
		double length = horizontalDistance(vector, Vec3d.ZERO);
		if (length == 0) {
			return Vec3d.ZERO;
		}
		return new Vec3d(vector.x / length, vector.y / length, vector.z / length);
	}

	private double[] lineIntersection(Vec3d firstPoint, Vec3d firstDirection, Vec3d secondPoint, Vec3d secondDirection) {
		double denominator = cross(firstDirection, secondDirection);
		if (Math.abs(denominator) < 0.0001) {
			return null;
		}

		Vec3d delta = secondPoint.subtract(firstPoint);
		return new double[] {
				cross(delta, secondDirection) / denominator,
				cross(delta, firstDirection) / denominator
		};
	}

	private double cross(Vec3d first, Vec3d second) {
		return first.x * second.z - first.z * second.x;
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

	private static class RadialSwitchLayout {
		private final List<VecYPR> straightPath;
		private final List<VecYPR> turnPath;
		private final double renderStep;
		private final double straightEnd;
		private final double turnEnd;
		private final double heelDistance;
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

		private RadialSwitchLayout(List<VecYPR> straightPath, List<VecYPR> turnPath, double renderStep, double straightEnd, double turnEnd, double heelDistance, double stretcherDistance, TrackModelPart straightFrogRail, TrackModelPart straightStockRail, TrackModelPart turnFrogRail, TrackModelPart turnStockRail, FrogGeometry frog) {
			this.straightPath = straightPath;
			this.turnPath = turnPath;
			this.renderStep = renderStep;
			this.straightEnd = straightEnd;
			this.turnEnd = turnEnd;
			this.heelDistance = heelDistance;
			this.stretcherDistance = stretcherDistance;
			this.straightFrogRail = straightFrogRail;
			this.straightStockRail = straightStockRail;
			this.turnFrogRail = turnFrogRail;
			this.turnStockRail = turnStockRail;
			double wingRailLength = renderStep;
			this.straightGapStartDistance = frog.straightWingDistance;
			this.curvedGapStartDistance = frog.curvedWingDistance;
			this.straightWingStartDistance = Math.max(heelDistance, frog.straightWingDistance - wingRailLength);
			this.curvedWingStartDistance = Math.max(heelDistance, frog.curvedWingDistance - wingRailLength);
			this.straightCheckDistance = Math.min(straightEnd, straightWingStartDistance);
			this.curvedCheckDistance = Math.min(turnEnd, curvedWingStartDistance);
			this.straightIntersectionDistance = frog.straightIntersectionDistance;
			this.curvedIntersectionDistance = frog.curvedIntersectionDistance;
			this.straightPointDistance = frog.straightPointDistance;
			this.curvedPointDistance = frog.curvedPointDistance;
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
