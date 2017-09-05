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

import com.robo4j.math.features.FeatureSet;
import com.robo4j.math.geometry.Point2f;
import com.robo4j.math.geometry.ScanResult2D;

/**
 * Features.
 * 
 * NOTE(Marcus/Aug 20, 2017): I have the feeling I want to send more stuff back.
 * If not, simply remove this class and just return the feature set.
 * 
 * @author Marcus
 */
public final class AnalysisResult {
	private final ScanResult2D source;
	private final FeatureSet features;
	private final Point2f targetPoint;
	private final Point2f centerPoint;

	public AnalysisResult(ScanResult2D source, FeatureSet features, Point2f targetPoint, Point2f centerPoint) {
		this.source = source;
		this.features = features;
		this.targetPoint = targetPoint;
		this.centerPoint = centerPoint;
	}

	public FeatureSet getFeatures() {
		return features;
	}

	public ScanResult2D getSource() {
		return source;
	}

	public Point2f getTargetPoint() {
		return targetPoint;
	}

	public Point2f getCenterPoint() {
		return centerPoint;
	}
}
