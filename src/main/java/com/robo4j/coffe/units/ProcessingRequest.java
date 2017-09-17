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

import com.robo4j.RoboReference;
import com.robo4j.math.geometry.ScanResult2D;

/**
 * Request for analyzing a scan.
 * 
 * @author Marcus
 */
public class ProcessingRequest {
	private final RoboReference<AnalysisResult> recipient;
	private final ScanResult2D scan;
	private final Scope scope;
	private final float angularResolution;

	public enum Scope {
		ALL, WALLS, WALLS_AND_CORNERS
	}

	/**
	 * Creates a request for analysis.
	 * 
	 * @param recipient
	 *            the recipient to send the result.
	 * @param scan
	 *            the scan to analyze.
	 */
	public ProcessingRequest(RoboReference<AnalysisResult> recipient, ScanResult2D scan, Scope scope, float angularResolution) {
		this.recipient = recipient;
		this.scan = scan;
		this.scope = scope;
		this.angularResolution = angularResolution;
	}

	public RoboReference<AnalysisResult> getRecipient() {
		return recipient;
	}

	public ScanResult2D getScan() {
		return scan;
	}

	public Scope getScope() {
		return scope;
	}

	public float getAngularResolution() {
		return angularResolution;
	}
}
