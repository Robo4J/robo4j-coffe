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

import com.robo4j.coffe.units.AnalysisResult;
import com.robo4j.coffe.units.ProcessingRequest;
import com.robo4j.coffe.units.ProcessingRequest.Scope;
import com.robo4j.core.ConfigurationException;
import com.robo4j.core.LocalReferenceAdapter;
import com.robo4j.core.RoboContext;
import com.robo4j.core.RoboReference;
import com.robo4j.core.RoboUnit;
import com.robo4j.core.configuration.Configuration;
import com.robo4j.core.logging.SimpleLoggingUtil;
import com.robo4j.hw.rpi.i2c.adafruitlcd.Color;
import com.robo4j.math.features.Raycast;
import com.robo4j.math.geometry.Point2f;
import com.robo4j.math.geometry.ScanResult2D;
import com.robo4j.units.rpi.lcd.LcdMessage;
import com.robo4j.units.rpi.lidarlite.ScanRequest;

/**
 * This is a simple high level mission controller for Coff-E.
 * 
 * @author Marcus Hirt
 */
public class MissionController extends RoboUnit<MissionControllerEvent> {
	private static final float ROTATION_SPEED = 1.0f;
	private static final float ANGULAR_RESOLUTION_FULL_SCAN = 0.3f;

	/**
	 * The reference id of the tank unit.
	 */
	public static final String KEY_ID_TANK = "tank";

	/**
	 * The reference id of the lcd
	 */
	public static final String KEY_ID_LCD = "lcd";

	/**
	 * The reference id of the scanner
	 */
	public static final String KEY_ID_SCANNER = "scanner";

	/**
	 * The reference id of the scan processor
	 */
	public static final String KEY_ID_SCAN_PROCESSOR = "scanProcessor";

	/**
	 * The mode of operation.
	 */
	public static final String KEY_MODE_OF_OPERATION = "modeOfOperation";

	private final ScannerDelegate scannerDelegate;
	private final AnalysisDelegate analysisDelegate;

	private volatile ModeOfOperation currentMode = ModeOfOperation.FASTEST_PATH;

	// Using this to make sure that we don't send multiple scan requests
	// concurrently.
	private final AtomicBoolean laserLock = new AtomicBoolean();
	private volatile FastestPathState currentPathState = FastestPathState.NMI;

	private String refIdLcd;
	private String refIdTank;
	private String refIdScanner;
	private String refIdScanProcessor;

	/**
	 * This is a simple delegate class for accepting the scan messages.
	 * NOTE(Marcus/Aug 30, 2017): Use the LocalReferenceAdapter here...
	 */
	private class ScannerDelegate extends RoboUnit<ScanResult2D> {
		private static final String ID_SCANNER_DELEGATE = "__scannerDelegate";

		public ScannerDelegate() {
			super(ScanResult2D.class, MissionController.this.getContext(), ID_SCANNER_DELEGATE);
		}

		@Override
		public void onMessage(ScanResult2D message) {
			receiveNewScan(message);
		}

		// Don't bother scheduling this - the processing of the results will be
		// scheduled in the worker pool anyways.
		@Override
		public void sendMessage(ScanResult2D message) {
			onMessage(message);
		}
	}

	/**
	 * This is a simple delegate class for accepting the analyzed scans.
	 * NOTE(Marcus/Aug 30, 2017): Use the LocalReferenceAdapter here...
	 */
	private class AnalysisDelegate extends RoboUnit<AnalysisResult> {
		private static final String ID_ANALYSIS_DELEGATE = "__analysisDelegate";

		public AnalysisDelegate() {
			super(AnalysisResult.class, MissionController.this.getContext(), ID_ANALYSIS_DELEGATE);
		}

		@Override
		public void onMessage(AnalysisResult message) {
			updateFromNewKnowledge(message);
		}

		// Don't bother scheduling this - the processing of the results will be
		// scheduled in the worker pool anyways.
		@Override
		public void sendMessage(AnalysisResult message) {
			onMessage(message);
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param ctx
	 *            the {@link RoboContext} representing Coff-E.
	 */
	public MissionController(RoboContext ctx, String id) {
		super(MissionControllerEvent.class, ctx, id);
		scannerDelegate = new ScannerDelegate();
		analysisDelegate = new AnalysisDelegate();
	}

	@Override
	protected void onInitialization(Configuration configuration) throws ConfigurationException {
		refIdTank = configuration.getString(KEY_ID_TANK, null);
		if (refIdTank == null) {
			throw ConfigurationException.createMissingConfigNameException(KEY_ID_TANK);
		}
		refIdLcd = configuration.getString(KEY_ID_LCD, null);
		if (refIdLcd == null) {
			throw ConfigurationException.createMissingConfigNameException(KEY_ID_LCD);
		}
		refIdScanner = configuration.getString(KEY_ID_SCANNER, null);
		if (refIdScanner == null) {
			throw ConfigurationException.createMissingConfigNameException(KEY_ID_SCANNER);
		}
		refIdScanProcessor = configuration.getString(KEY_ID_SCAN_PROCESSOR, null);
		if (refIdScanProcessor == null) {
			throw ConfigurationException.createMissingConfigNameException(KEY_ID_SCAN_PROCESSOR);
		}
		currentMode = getModeOfOperation(configuration);
	}

	private ModeOfOperation getModeOfOperation(Configuration configuration) {
		String modeString = configuration.getString(KEY_MODE_OF_OPERATION, ModeOfOperation.FASTEST_PATH.toString());
		return ModeOfOperation.valueOf(modeString.toUpperCase());
	}

	@Override
	public void onMessage(MissionControllerEvent message) {
		switch (message) {
		case START:
			getLcdUnit().sendMessage(new LcdMessage("Starting...", Color.TEAL));
			reset();
			switch (currentMode) {
			case FASTEST_PATH:
				initiateFastestPathMode();
				break;
			default:
				getTank().sendMessage(new TankEvent(0, 0, 0));
				SimpleLoggingUtil.error(MissionController.class, "Mode not supported:" + message);
			}
		case STOPPED_ROTATING:
			// Release the latch and continue.
		}
	}

	private void initiateFastestPathMode() {
		currentMode = ModeOfOperation.FASTEST_PATH;
		currentPathState = FastestPathState.NMI;
		printMessage(Color.BLUE, "Starting\nMission!");
		scheduleFullScan();
	}

	private void scheduleFullScan() {
		if (laserLock.compareAndSet(false, true)) {
			RoboReference<ScanRequest> scanner = getScannerUnit();
			scanner.sendMessage(new ScanRequest(scannerDelegate, ScanRequest.ScanAction.ONCE, -45f, 90f, ANGULAR_RESOLUTION_FULL_SCAN));
		}
	}

	private void receiveNewScan(ScanResult2D message) {
		laserLock.set(false);
		// Send to feature extractor on the worker thread.
		getScanProcessor().sendMessage(new ProcessingRequest(analysisDelegate, message, Scope.ALL, message.getAngularResolution()));
	}

	private void updateFromNewKnowledge(AnalysisResult message) {
		getTank().sendMessage(new TankEvent(new LocalReferenceAdapter<RotationDoneNotification>(RotationDoneNotification.class) {
			@Override
			public void sendMessage(RotationDoneNotification message) {
				if (message == RotationDoneNotification.ROTATION_COMPLETE) {
					updateState(FastestPathState.MOVE_TO_TARGET);
					startMoveToTarget();
				}
			}
		}, ROTATION_SPEED, /* TODO: add turn direction from here */ 0f, message.getTargetPoint().getAngle()));
	}

	private void reset() {
		getTank().sendMessage(new TankEvent(0, 0, 0));
	}

	private RoboReference<TankEvent> getTank() {
		return getContext().getReference(refIdTank);
	}

	private void printMessage(Color color, String message) {
		getLcdUnit().sendMessage(new LcdMessage(message, color));
	}

	private RoboReference<LcdMessage> getLcdUnit() {
		return getContext().getReference(refIdLcd);
	}

	private RoboReference<ScanRequest> getScannerUnit() {
		return getContext().getReference(refIdScanner);
	}

	private RoboReference<ProcessingRequest> getScanProcessor() {
		return getContext().getReference(refIdScanProcessor);
	}

	public RoboUnit<?> getDelegate() {
		return scannerDelegate;
	}

	private void updateState(FastestPathState newState) {
		printMessage(newState.getStateColor(), newState.getHumanFriendlyName());
	}

	private void startMoveToTarget() {
		System.out.println("Moving to target");
		// 1. Quick scan...
		// 2. Analyze quick scan...
		// 3. If not too close, update speed and direction...
		// 4. If too close, enter NMI.
	}
}
