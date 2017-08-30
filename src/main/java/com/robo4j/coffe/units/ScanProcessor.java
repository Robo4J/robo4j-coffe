/*
 * Copyright (c) 2014, 2017, Marcus Hirt, Miroslav Wengner
 * 
 * Robo4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Robo4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Robo4J. If not, see <http://www.gnu.org/licenses/>.
 */
package com.robo4j.coffe.units;

import com.robo4j.core.RoboContext;
import com.robo4j.core.RoboUnit;
import com.robo4j.core.WorkTrait;
import com.robo4j.math.features.FeatureExtraction;
import com.robo4j.math.features.FeatureSet;
import com.robo4j.math.features.Raycast;
import com.robo4j.math.geometry.Point2f;

/**
 * This unit analyzes scans.
 * 
 * NOTE(Marcus/Aug 20, 2017): This is a general unit, that can be used without
 * any dependencies to hardware. Maybe create new core module for hardware
 * independent robotics modules?
 * 
 * @author Marcus
 */
@WorkTrait
public class ScanProcessor extends RoboUnit<ProcessingRequest> {
	private static final float MIN_LATERAL_DISTANCE = 0.32f; // in meters
	private static final float RAYCASTING_STEP_ANGLE = (float) Math.toRadians(0.4f);
	private static final float DETAILED_RAYCASTING_STEP_ANGLE = (float) Math.toRadians(0.2f);
	private static final float ONE_DEGREE = (float) Math.toRadians(1.0);

	/**
	 * Constructor.
	 * 
	 * @param context
	 *            the RoboContext
	 * @param id
	 *            the id of the ScanProcessor unit.
	 */
	public ScanProcessor(RoboContext context, String id) {
		super(ProcessingRequest.class, context, id);
	}

	@Override
	public void onMessage(ProcessingRequest message) {
		super.onMessage(message);
		// NOTE(Marcus/Aug 20, 2017): The feature extraction is computationally
		// expensive, but will not hold up the system scheduler, since this unit
		// is marked as @WorkTrait.
		FeatureSet features = FeatureExtraction.getFeatures(message.getScan().getPoints(), message.getAngularResolution());
		Point2f targetPoint = Raycast.raycastMostPromisingPoint(message.getScan().getPoints(), MIN_LATERAL_DISTANCE,
				DETAILED_RAYCASTING_STEP_ANGLE, features);

		message.getRecipient().sendMessage(new AnalysisResult(message.getScan(), features, targetPoint));
	}
}
