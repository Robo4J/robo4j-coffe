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

import java.util.concurrent.TimeUnit;

import com.robo4j.ConfigurationException;
import com.robo4j.RoboContext;
import com.robo4j.RoboUnit;
import com.robo4j.configuration.Configuration;
import com.robo4j.units.rpi.pwm.MC33926HBridgeUnit;

/**
 * Controls the claw.
 * 
 * @see MC33926HBridgeUnit for the rest of the property keys for configuring the
 *      claw.
 * 
 * @author Marcus
 */
public class ClawUnit extends RoboUnit<ClawState> {
	/**
	 * Time to open claw.
	 */
	private static final String PROPERTY_KEY_OPEN_TIME = "openTime";

	/**
	 * Time to close claw.
	 */
	private static final String PROPERTY_KEY_CLOSE_TIME = "closeTime";

	/**
	 * The default state of the claw. Must match the initial hardware state. On
	 * shutdown, will return to this state.
	 */
	private static final String PROPERTY_KEY_DEFAULT_STATE = "defaultState";

	private static final Float OPEN_SPEED = 1.0f;
	private static final Float CLOSE_SPEED = -1.0f;
	private static final Float STOP_SPEED = 0f;

	private final MC33926HBridgeUnit delegate;
	private volatile boolean inTransit = false;
	private ClawState defaultState;
	private volatile ClawState state;
	private long openTime;
	private long closeTime;

	public ClawUnit(RoboContext context, String id) {
		super(ClawState.class, context, id);
		delegate = new MC33926HBridgeUnit(context, "__" + id + "_delegate");
	}

	@Override
	public void shutdown() {
		// Trying to wait until any ongoing transition is done.
		// This can of course fail miserably if there is a lot of claw action.
		while (inTransit) {
			sleep(100);
		}
		// Always leave the claw open!
		onMessage(defaultState);

		// This is ugly, and it will hold up the shutdown, but we really must
		// wait until we've opened the claw.
		while (inTransit) {
			sleep(100);
		}
		super.shutdown();
	}

	@Override
	protected void onInitialization(Configuration configuration) throws ConfigurationException {
		delegate.initialize(configuration);
		String clawState = configuration.getString(PROPERTY_KEY_DEFAULT_STATE, "OPEN");
		defaultState = ClawState.valueOf(clawState);
		state = defaultState;
		openTime = configuration.getLong(PROPERTY_KEY_OPEN_TIME, -1L);
		if (openTime == -1) {
			throw ConfigurationException.createMissingConfigNameException(PROPERTY_KEY_OPEN_TIME);
		}
		closeTime = configuration.getLong(PROPERTY_KEY_CLOSE_TIME, -1L);
		if (closeTime == -1) {
			throw ConfigurationException.createMissingConfigNameException(PROPERTY_KEY_CLOSE_TIME);
		}
	}

	@Override
	public void onMessage(ClawState message) {
		super.onMessage(message);
		// Do not accept new input before we're (usually) done. Do not accept
		// new input if it does not change the state.
		if (!inTransit && state != message) {
			inTransit = true;
			delegate.sendMessage(message == ClawState.OPEN ? OPEN_SPEED : CLOSE_SPEED);
			getContext().getScheduler().schedule(delegate, STOP_SPEED, message == ClawState.OPEN ? openTime : closeTime, 0,
					TimeUnit.MILLISECONDS, 1, (RoboContext) -> state = message);
		}
	}

	private void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// Do not care
		}
	}
}
