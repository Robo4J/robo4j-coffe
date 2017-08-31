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
package com.robo4j.coffe.controllers;

import com.robo4j.core.RoboReference;

/**
 * The tank will either do a gyro based rotation or run in a specified direction
 * (rotate == 0). If the rotation is set, the tank will use the speed and
 * direction, but still use the gyro to decide when done.
 * 
 * @author Marcus
 */
public class TankEvent {
	private final RoboReference<RotationDoneNotification> rotationDoneListener;
	private final float speed;
	private final float direction;
	private final float rotate;

	/**
	 * Constructor.
	 * 
	 * @param rotationDoneListener
	 *            if rotation set, will trigger when rotated the specified
	 *            amount.
	 * @param speed
	 *            normalized speed
	 * @param direction
	 *            in radians
	 * @param rotate
	 *            in radians
	 */
	public TankEvent(RoboReference<RotationDoneNotification> rotationDoneListener, float speed, float direction, float rotate) {
		this.rotationDoneListener = rotationDoneListener;
		this.speed = speed;
		this.direction = direction;
		this.rotate = rotate;
	}

	public TankEvent(int speed, int direction, int rotation) {
		this(null, speed, direction, rotation);
	}

	public float getSpeed() {
		return speed;
	}

	public float getDirection() {
		return direction;
	}

	public float getRotate() {
		return rotate;
	}

	public RoboReference<RotationDoneNotification> getRotationDoneListener() {
		return rotationDoneListener;
	}
}
