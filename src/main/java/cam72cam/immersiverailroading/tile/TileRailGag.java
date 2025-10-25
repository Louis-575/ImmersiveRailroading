package cam72cam.immersiverailroading.tile;

import cam72cam.immersiverailroading.ConfigGraphics;

public class TileRailGag extends TileRailBase {
	public void setFlexible(boolean flexible) {
		this.flexible = flexible;
	}

	public double getRenderDistance() {
		return ConfigGraphics.TrackRenderDistance;
	}
}