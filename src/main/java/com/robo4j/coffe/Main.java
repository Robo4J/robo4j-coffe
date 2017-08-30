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
package com.robo4j.coffe;

import java.io.IOException;

import com.robo4j.coffe.controllers.MissionControllerEvent;
import com.robo4j.coffe.units.LocalReferenceAdapter;
import com.robo4j.core.ConfigurationException;
import com.robo4j.core.RoboBuilder;
import com.robo4j.core.RoboBuilderException;
import com.robo4j.core.RoboContext;
import com.robo4j.core.logging.SimpleLoggingUtil;
import com.robo4j.core.util.SystemUtil;
import com.robo4j.hw.rpi.i2c.adafruitlcd.Color;
import com.robo4j.units.rpi.gyro.GyroEvent;
import com.robo4j.units.rpi.gyro.GyroRequest;
import com.robo4j.units.rpi.gyro.GyroRequest.GyroAction;
import com.robo4j.units.rpi.lcd.LcdMessage;

/**
 * The main class for running Coff-E.
 * 
 * @author Marcus
 */
public class Main {

	/**
	 * Starts Coff-E.
	 * 
	 * @param args
	 *            currently ignored.
	 * @throws RoboBuilderException
	 *             if there was any problem setting up Coff-E.
	 * @throws IOException
	 * @throws ConfigurationException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws RoboBuilderException, IOException, ConfigurationException, InterruptedException {
		SimpleLoggingUtil.print(Main.class, "Starting Coff-E.\nLoading system...");
		RoboBuilder builder = new RoboBuilder(Main.class.getClassLoader().getResourceAsStream("system.xml"));
		builder.add(Main.class.getClassLoader().getResourceAsStream("units.xml"));
		final RoboContext ctx = builder.build();

		SimpleLoggingUtil.print(Main.class, "System loaded. Starting...");

		System.out.println("State before start:");
		System.out.println(SystemUtil.printStateReport(ctx));
		ctx.start();

		SimpleLoggingUtil.print(Main.class, "System started.");
		System.out.println("State after start:");
		System.out.println(SystemUtil.printStateReport(ctx));

		System.out.println("Starting calibration (do not touch anything!):");
		ctx.getReference("lcd").sendMessage(new LcdMessage("Calibrating...\nBe still!", Color.RED));
		ctx.getReference("gyro").sendMessage(new GyroRequest(new LocalReferenceAdapter<GyroEvent>(GyroEvent.class) {
			@Override
			public void sendMessage(GyroEvent event) {
				ctx.getReference("lcd").sendMessage(new LcdMessage("Starting Coff-E!\nStay clear... ;)", Color.YELLOW));
				ctx.getReference("missioncontroller").sendMessage(MissionControllerEvent.START);
			}
		}, GyroAction.CALIBRATE, null));

		System.out.println("Press enter to quit!");
		System.in.read();
		ctx.shutdown();
	}
}
