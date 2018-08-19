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

import com.robo4j.ConfigurationException;
import com.robo4j.LocalReferenceAdapter;
import com.robo4j.RoboContext;
import com.robo4j.RoboReference;
import com.robo4j.RoboUnit;
import com.robo4j.coffe.units.AnalysisResult;
import com.robo4j.coffe.units.ProcessingRequest;
import com.robo4j.coffe.units.ProcessingRequest.Scope;
import com.robo4j.configuration.Configuration;
import com.robo4j.hw.rpi.i2c.adafruitlcd.Color;
import com.robo4j.logging.SimpleLoggingUtil;
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
	// FIXME(Marcus/Sep 5, 2017): Does not belong here...
	private static final boolean IS_USING_TRACKS = false;
	private static final float DEGREES_25_IN_RAD = (float) Math.toRadians(25);

	private static final float ROTATION_SPEED = 1.0f;
	private static final float ANGULAR_RESOLUTION_FULL_SCAN = 0.4f;
	private static final float ANGULAR_RESOLUTION_FAST_SCAN = 1f;

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
	// Robo4J head location
	private static final Point2f ORIGO = Point2f.fromPolar(0, 0);
	// If closer than this, get more info
	private static final float MIN_GOAL_RANGE = 0.5f;
	private static final TankEvent STOP_MESSAGE = new TankEvent(0, 0, 0);

	private final ScannerDelegate scannerDelegate;
	private final RangeDelegate rangeDelegate;
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

	private class ScannerDelegate extends LocalReferenceAdapter<ScanResult2D> {
		public ScannerDelegate() {
			super(ScanResult2D.class);
		}

		// Don't bother scheduling this - the processing of the results will be
		// scheduled in the worker pool anyways.
		@Override
		public void sendMessage(ScanResult2D message) {
			receiveScan(message);
		}
	}

	private class RangeDelegate extends LocalReferenceAdapter<ScanResult2D> {
		public RangeDelegate() {
			super(ScanResult2D.class);
		}

		// Don't bother scheduling this - the processing of the results will be
		// scheduled in the worker pool anyways.
		@Override
		public void sendMessage(ScanResult2D message) {
			receiveRangeScan(message);
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
		rangeDelegate = new RangeDelegate();
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
			scanner.sendMessage(new ScanRequest(scannerDelegate, -45f, 90f, ANGULAR_RESOLUTION_FULL_SCAN));
		}
	}

	private void scheduleQuickScan() {
		if (laserLock.compareAndSet(false, true)) {
			RoboReference<ScanRequest> scanner = getScannerUnit();
			scanner.sendMessage(new ScanRequest(scannerDelegate, -30f, 60f, ANGULAR_RESOLUTION_FAST_SCAN));
		}
	}

	private void receiveScan(ScanResult2D message) {
		laserLock.set(false);
		// Send to feature extractor on the worker thread.
		getScanProcessor().sendMessage(new ProcessingRequest(analysisDelegate, message, Scope.ALL, message.getAngularResolution()));
	}

	private void receiveRangeScan(ScanResult2D message) {
		// We only care about these if we are cornered...
		laserLock.set(false);
		if (currentPathState == FastestPathState.CORNERED) {
			if (message.getFarthestPoint().getRange() > 1.4) {
				getTank().sendMessage(STOP_MESSAGE);
				updateState(FastestPathState.NMI);
				scheduleFullScan();
			} else {
				scheduleRangeMeasurement(message.getFarthestPoint().getAngle() > 0);
			}
		}
	}

	private void updateFromNewKnowledge(final AnalysisResult message) {
		if (currentPathState == FastestPathState.NMI) {
			if (isCornered(message)) {
				getTank().sendMessage(STOP_MESSAGE);
				updateState(FastestPathState.CORNERED);
				printMessage(Color.RED, String.format("CornerEscape:\nG@%2.1fm,%2.1fdeg", message.getTargetPoint().getRange(),
						Math.toDegrees(message.getTargetPoint().getAngle())));
				Point2f p = message.getFeatures().getClosestCorner();
				boolean goRight = false;
				if (p != null && p.getRange() < 1) {
					goRight = p.getAngle() > 0;
				} else {
					float rangeLeft = message.getSource().getLeftmostPoint().getRange();
					float rangeRight = message.getSource().getRightmostPoint().getRange();
					goRight = rangeRight > rangeLeft;
				}
				printMessage(Color.BLUE, "Corner escape\nRotating...");
				scheduleRangeMeasurement(goRight);
				// We don't want to get notified from the Gyro, but rather the
				// laser, hence no "rotation" in the tank sense.
				getTank().sendMessage(new TankEvent(0.5f, goRight ? TankController.getRotationDirectionRight(IS_USING_TRACKS)
						: TankController.getRotationDirectionLeft(IS_USING_TRACKS), 0f));
			} else {
				getTank().sendMessage(new TankEvent(new LocalReferenceAdapter<RotationDoneNotification>(RotationDoneNotification.class) {
					@Override
					public void sendMessage(RotationDoneNotification rotMessage) {
						try {
							if (rotMessage == RotationDoneNotification.ROTATION_COMPLETE) {
								getTank().sendMessage(new TankEvent(0, 0, 0));
								startMoveToTarget(message);
							}
						} catch (Throwable t) {
							SimpleLoggingUtil.debug(getClass(), "Move to target failed.", t);
						}
					}
				}, ROTATION_SPEED, /* TODO: add turn direction from here */ 0f, message.getTargetPoint().getAngle()));
				printMessage(Color.YELLOW, String.format("Rotating...\nR:%2.1f A:%2.1f", message.getTargetPoint().getRange(),
						Math.toDegrees(message.getTargetPoint().getAngle())));
			}
		} else if (currentPathState == FastestPathState.MOVE_TO_TARGET) {
			if (message.getTargetPoint().distance(ORIGO) < MIN_GOAL_RANGE) {
				getTank().sendMessage(STOP_MESSAGE);
				updateState(FastestPathState.NMI);
				scheduleFullScan();
			} else {
				float speedMultiplier = (float) Math.min(message.getSource().getNearestPoint().getRange(),
						message.getTargetPoint().getRange() / 2.0);
				float speed = 0;
				float direction = 0;
				Point2f mostPromising = message.getTargetPoint();
				Point2f straightAhead = message.getCenterPoint();
				if (isGoodEnough(mostPromising, straightAhead)) {
					direction = 0;
					mostPromising = straightAhead;
				} else {
					// direction = mostPromising.getAngle() /
					// (tank.isUsingTracks() ? 2.5f : 1f);
					if (IS_USING_TRACKS) {
						direction = mostPromising.getAngle();
					} else {
						direction = mostPromising.getAngle();
						// Turns way faster on wheels...
						direction = (float) ((0.1 + Math.min(1.0f, direction / 5) * 0.9f) * mostPromising.getAngle()) / 2;
					}
				}

				double targetDirectionDegrees = Math.toDegrees(mostPromising.getAngle());
				if (speedMultiplier < 1.0) {
					speed = speedMultiplier;
				} else {
					speed = 1;
				}
				printMessage(Color.BLUE,
						String.format("Goal: A:%2.0f R:%2.1fm\nNear: A:%2.0f R:%2.1fm", targetDirectionDegrees, mostPromising.getRange(),
								Math.toDegrees(message.getSource().getNearestPoint().getAngle()),
								message.getSource().getNearestPoint().getRange()));
				getTank().sendMessage(new TankEvent(speed, direction, 0f));
				scheduleQuickScan();
			}
		}
	}

	private boolean isGoodEnough(Point2f goal, Point2f straightAhead) {
		if (Math.abs(goal.getAngle()) > DEGREES_25_IN_RAD) {
			// want to turn badly...
			return false;
		} else if (straightAhead.getRange() >= 4) {
			// 4 meters? Run forrest, run!
			return true;
		}
		return goal.rangeDifference(straightAhead) < 0.25 * goal.getRange();
	}

	private void scheduleRangeMeasurement(boolean goRight) {
		if (laserLock.compareAndSet(false, true)) {
			getScannerUnit().sendMessage(new ScanRequest(rangeDelegate, goRight ? 10 : -10, 0, 0));
		} else {
			SimpleLoggingUtil.debug(getClass(), "Failed to schedule range measurement due to laser lock!");
		}
	}

	private boolean isCornered(AnalysisResult message) {
		return message.getTargetPoint().distance(ORIGO) < 0.5;
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

	public RoboReference<ScanResult2D> getScannerDelegate() {
		return scannerDelegate;
	}

	private void updateState(FastestPathState newState) {
		currentPathState = newState;
		printMessage(newState.getStateColor(), newState.getHumanFriendlyName());
	}

	private void startMoveToTarget(AnalysisResult message) {
		updateState(FastestPathState.MOVE_TO_TARGET);
		printMessage(Color.GREEN, String.format("Moving to target\nR: %2.1f A: %2.1f", message.getTargetPoint().getRange(), 0f));
		scheduleQuickScan();
		// Full speed ahead!
		getTank().sendMessage(new TankEvent(1.0f, 0f, 0f));
	}
}
