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

import com.robo4j.hw.rpi.i2c.adafruitlcd.Color;

/**
 * These are the states that Coff-E will move when in the
 * {@link ModeOfOperation#FASTEST_PATH}.
 * 
 * @author Marcus
 */
public enum FastestPathState {
	/**
	 * Coff-E decided he needs more information. Is also the start state.
	 */
	NMI(Color.BLUE, "Gathering Info"),
	/**
	 * Coff-E decided he is cornered (there are no good paths to travel).
	 */
	CORNERED(Color.RED, "Cornered"),
	/**
	 * Coff-E has acquired a target, and is moving towards it, making
	 * adjustments as he goes.
	 */
	MOVE_TO_TARGET(Color.TEAL, "Moving to Target");

	Color stateColor;
	String humanFriendlyName;
	
	FastestPathState(Color stateColor, String humanFriendlyName) {
		this.stateColor = stateColor;
		this.humanFriendlyName = humanFriendlyName;
	}
	
	public Color getStateColor() {
		return stateColor;
	}
	
	public String getHumanFriendlyName() {
		return humanFriendlyName;
	}
}
