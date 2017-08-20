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

/**
 * This is the mode of operation for the mission controller. When GPS is
 * supported, target coordinates must be supplied.
 * 
 * @author Marcus
 */
public enum ModeOfOperation {
	/**
	 * This mode will attempt to always look for the path that would allow
	 * Coff-E to go as far away as possible without hindrance, allowing him
	 * to go as fast as possible.
	 */
	FASTEST_PATH,
	/**
	 * This mode will use a list of GPS coordinates to visit, and will
	 * attempt to navigate all coordinates in order.
	 * 
	 * FIXME(Marcus/Aug 19, 2017): To be implemented. A*?
	 */
	GPS,
	/**
	 * This will run Coff-E in simultaneous location and mapping, attempting
	 * to build awareness of the world as he goes.
	 * 
	 * FIXME(Marcus/Aug 19, 2017): To be implemented. Should also allow GPS
	 * coordinates.
	 */
	FAST_SLAM
}