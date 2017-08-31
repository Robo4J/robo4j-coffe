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

import java.util.concurrent.atomic.AtomicBoolean;

import com.robo4j.core.ConfigurationException;
import com.robo4j.core.RoboContext;
import com.robo4j.core.RoboReference;
import com.robo4j.core.RoboUnit;
import com.robo4j.core.configuration.Configuration;
import com.robo4j.core.logging.SimpleLoggingUtil;
import com.robo4j.math.geometry.Tuple3f;
import com.robo4j.units.rpi.gyro.GyroEvent;
import com.robo4j.units.rpi.gyro.GyroRequest;
import com.robo4j.units.rpi.gyro.GyroRequest.GyroAction;
import com.robo4j.units.rpi.roboclaw.MotionEvent;

/**
 * Abstraction for the vehicle actuators. Note that all the calls will be
 * scheduled to the individual units on all cores - this is just to simplify the
 * usage.
 * 
 * @author Marcus
 */
public class TankController extends RoboUnit<TankEvent> {
	private static final float DEGREES_90 = (float) Math.toRadians(90);
	private static final float DEGREES_270 = (float) Math.toRadians(270);
	private static final float DEGREES_80 = (float) Math.toRadians(90);
	private static final float DEGREES_280 = (float) Math.toRadians(270);

	// FIXME(Marcus/Aug 19, 2017): These could be made configurable.
	private static final String REF_ID_MOTION = "motion";
	private static final String REF_ID_GYRO = "gyro";

	/**
	 * This controls rotations and turns. Set it to true if you have tracks on
	 * Coff-E. Set it to false if you are using two wheels and a cart wheel.
	 */
	public static final String KEY_IS_USING_TRACKS = "useTracks";

	/**
	 * This controls the maximum speed that Coff-E will run at.
	 */
	public static final String KEY_MAX_SPEED = "maxSpeed";

	private final RoboContext ctx;
	private final AtomicBoolean isRotating = new AtomicBoolean(false);
	private final GyroDelegate gyroDelegate;

	private volatile float targetAngle;
	private float maxSpeed = 1.0f;
	private boolean isUsingTracks = false;

	private class GyroDelegate extends RoboUnit<GyroEvent> {
		public GyroDelegate() {
			super(GyroEvent.class, TankController.this.getContext(), "__TankGyroDelegate");
		}

		@Override
		public void onMessage(GyroEvent message) {
			super.onMessage(message);
			processGyroEvent(message);
		}

		@Override
		public void sendMessage(GyroEvent message) {
			// Just process it directly.
			onMessage(message);
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param ctx
	 *            the RoboSystem.
	 */
	public TankController(RoboContext ctx, String id) {
		super(TankEvent.class, ctx, id);
		this.ctx = ctx;
		gyroDelegate = new GyroDelegate();
	}

	/**
	 * Sets the speed of the tank.
	 * 
	 * @param speed
	 *            the desired speed.
	 * @param direction
	 *            the desired direction.
	 */
	public void setSpeed(float speed, float direction) {
		RoboReference<MotionEvent> reference = ctx.getReference(REF_ID_MOTION);
		if (reference != null) {
			reference.sendMessage(new MotionEvent(speed * maxSpeed, direction));
		} else {
			SimpleLoggingUtil.error(TankController.class, "Could not find the reference for " + REF_ID_MOTION);
		}
	}

	/**
	 * Returns the Robo4J context where all the units are specified.
	 * 
	 * @return the Robo4J context.
	 */
	public RoboContext getContext() {
		return ctx;
	}

	public void stop() {
		stopRotating();
		setSpeed(0, 0);
	}

	private void stopRotating() {
		isRotating.set(false);
		getGyro().sendMessage(new GyroRequest(gyroDelegate, GyroAction.STOP, null));
	}
	
	public void rotate(TankEvent message) {
		if (isRotating.compareAndSet(false, true)) {
			targetAngle = (float) Math.toDegrees(message.getRotate());
			getGyro().sendMessage(new GyroRequest(gyroDelegate, GyroAction.CONTINUOUS, new Tuple3f(GyroRequest.DO_NOT_CARE, GyroRequest.DO_NOT_CARE, 1.0f)));
			float direction = 0;
			if (isUsingTracks) {
				direction = message.getRotate() > 0 ? DEGREES_80 : DEGREES_280;
			} else {
				direction = message.getRotate() > 0 ? DEGREES_90 : DEGREES_270;
			}
			setSpeed(message.getSpeed(), direction);
		}
	}

	/**
	 * Calibrates the tank gyro. Make sure this is done on startup.
	 */
	public void calibrate() {
		getGyro().sendMessage(new GyroRequest(gyroDelegate, GyroAction.CALIBRATE, null));
	}

	private RoboReference<GyroRequest> getGyro() {
		return getContext().getReference(REF_ID_GYRO);
	}

	@Override
	protected void onInitialization(Configuration configuration) throws ConfigurationException {
		isUsingTracks = configuration.getBoolean(KEY_IS_USING_TRACKS, false);
		maxSpeed = configuration.getFloat(KEY_MAX_SPEED, 1.0f);
	}

	private void processGyroEvent(GyroEvent message) {
		if (isRotating.get()) {
			if (isDoneRotating(message.getAngles().z)) {
				stopRotating();
				notifyRotationTarget();
				isRotating.set(true);
			}
		}
	}

	private boolean isDoneRotating(double currentAngle) {
		if (targetAngle > 0) {
			return currentAngle >= targetAngle;
		} else {
			return targetAngle >= currentAngle;
		}
	}

	private void notifyRotationTarget() {

	}

	@Override
	public void onMessage(TankEvent message) {
		super.onMessage(message);
		if (message.getRotate() != 0) {
			rotate(message);
		} else {
			setSpeed(message.getSpeed(), message.getDirection());
		}
	}
}
