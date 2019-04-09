/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.handler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.device.RfLinkDevice;
import org.openhab.binding.rflink.device.RfLinkDeviceFactory;
import org.openhab.binding.rflink.device.RfLinkRtsDevice;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RfLinkRtsPositionHandler} is a <b>Somfy RTS RollerShutters <u>POSITION</u> tracker</b>.
 * <p/>
 * This handler is triggered from the {@link RfLinkHandler}, when a {@link Command} is sent to an eligible
 * {@link RfLinkRtsDevice} (see {@link RfLinkDeviceConfiguration} configuration for more details).
 * <p/>
 * <b>THE CHALLENGE :</b> Somfy RollerShutters are "passive" things. They receive order from remote(s), but never
 * transmit status/position. So it is a bit difficult to track them.
 * <p/>
 * <b>HOW IT WORKS :</b> The RfLink bridge is able to listen to the same events as the physical RollerShutter.
 * Therefore, by knowing how long it takes to move from full CLOSE to full OPEN (see configuration), the handler is able
 * to guess the physical Shutter's moves and position.
 *
 * @author cartemere - initial Contribution. Handle UP/DOWN/STOP/PercentType events, scheduler based
 *
 */
public class RfLinkRtsPositionHandler {

    private Logger logger = LoggerFactory.getLogger(RfLinkRtsPositionHandler.class);

    // the RtsShutterInfos identifier
    private RfLinkHandler handler;
    // the duration for the shutter to move from full open to full closed
    private long shutterEffectiveDuration;

    // shutter position on the last Event
    private PercentType positionFrom = PercentType.ZERO;
    // shutter position status : "realtime" tracking
    private PercentType positionStatus = PercentType.ZERO;
    // input Command registered : STOP, UP, DOWN, PERCENT(**)
    // private Command commandProcessedRaw = StopMoveType.STOP;
    // input Command converted to an effective action : STOP, UP, DOWN
    private Command commandProcessedEffective = StopMoveType.STOP;
    // timestamp on the last action
    private long timestampOnLastEvent = System.currentTimeMillis();
    private long statusRefreshRate = 1000;
    private ScheduledFuture<?> schedulerStatus = null;
    private ScheduledFuture<?> schedulerTarget = null;

    public RfLinkRtsPositionHandler(RfLinkHandler handler) {
        this.handler = handler;
        this.shutterEffectiveDuration = handler.getConfiguration().shutterDuration * 1000;
        updateShutterPositionState(positionFrom);
    }

    /**
     * main entry point for the RtsPositionHandler
     *
     * @param rtsDevice the input {@link RfLinkRtsDevice} event to handle (must be initialized, with enclosed
     *                      {@link RfLinkRtsMessage} and {@link Command}
     */
    public synchronized void handleCommand(RfLinkRtsDevice rtsDevice) {
        // STEP 0 : stop all scheduled task on previous command (if any)
        stopSchedulerStatus(true);
        stopSchedulerTarget(true);
        // STEP 1 : handle what was ongoing BEFORE the current Command
        handlePreviousCommand();
        // STEP 2 : handle the current Command
        handleCurrentCommand(rtsDevice);
    }

    private void handlePreviousCommand() {
        if (StopMoveType.STOP.equals(commandProcessedEffective)) {
            // if previous command was STOP, it has already been processed
        } else {
            positionFrom = computeSnapshotPositionFromDelay();
            updateShutterPositionState(positionFrom);
        }
    }

    private void handleCurrentCommand(RfLinkRtsDevice rtsDevice) {
        timestampOnLastEvent = System.currentTimeMillis();
        Command command = rtsDevice.getCommand();
        logger.info("> received Command=" + command + " for device " + rtsDevice);
        if (StopMoveType.STOP.equals(command)) {
            commandProcessedEffective = command;
            sendDeviceCommand(rtsDevice);
            // nothing to schedule
        } else if (command instanceof PercentType) {
            PercentType targetPosition = (PercentType) command;
            int moveValue = getMoveFromTargetPosition(targetPosition);
            commandProcessedEffective = getDirectionCommandFromMove(moveValue);
            sendCommand(commandProcessedEffective);
            if (commandProcessedEffective instanceof UpDownType) {
                long delayToTarget = computeDelayFromMoveValue(moveValue);
                Command commandAtTarget = getCommandAtTargetPosition(targetPosition);
                schedulePositionTarget(delayToTarget, commandAtTarget);
            }
        } else if (command instanceof UpDownType) {
            commandProcessedEffective = command;
            // PercentType targetPosition = getMoveFromDirectionCommand(command);
            // int moveValue = getMoveFromTargetPosition(targetPosition);
            // long delayToTarget = computeDelayFromMoveValue(moveValue);
            sendDeviceCommand(rtsDevice);
            schedulePositionRefresh();
        } else {
            logger.error("Provided RfLinkDevice " + rtsDevice + " does not hold a valid command : " + command);
        }
    }

    private Command getCommandAtTargetPosition(PercentType targetPosition) {
        Command commandAtTarget;
        if (isPositionBoundReached(targetPosition)) {
            // move to the mechanical stop : no need to explicitly STOP
            commandAtTarget = null;
        } else {
            commandAtTarget = StopMoveType.STOP;
        }
        return commandAtTarget;
    }

    private boolean isPositionBoundReached(PercentType position) {
        return PercentType.HUNDRED.equals(position) || PercentType.ZERO.equals(position);
    }

    private void schedulePositionRefresh() {
        // update position at a fixed rate while moving
        logger.debug("SCHEDULE position update at " + statusRefreshRate + "ms");
        schedulerStatus = handler.getScheduler().scheduleWithFixedDelay(() -> {
            logger.debug("SCHEDULE: Update rolling status on " + handler.getThing().getThingTypeUID());
            positionStatus = computeSnapshotPositionFromDelay();
            updateShutterPositionState(positionStatus);
            if (isPositionBoundReached(positionStatus)) {
                // can not go further, stop refreshing the status
                stopSchedulerStatus(false);
            }
        }, statusRefreshRate, statusRefreshRate, TimeUnit.MILLISECONDS);
    }

    private void schedulePositionTarget(long delayTillCommandEnd, Command sendCommandAtTarget) {
        // update position at target position
        logger.debug("SCHEDULE position TARGET at " + delayTillCommandEnd + "ms with command=" + sendCommandAtTarget);
        schedulerTarget = handler.getScheduler().schedule(() -> {
            logger.debug("SCHEDULE: Update final status on " + handler.getThing().getUID());
            stopSchedulerTarget(false);
            positionFrom = computeSnapshotPositionFromDelay();
            updateShutterPositionState(positionFrom);
            sendCommand(sendCommandAtTarget);
        }, delayTillCommandEnd, TimeUnit.MILLISECONDS);
    }

    private void updateShutterPositionState(PercentType position) {
        logger.debug("update position to " + position + " on " + handler.getThing().getUID());
        handler.updateState(new ChannelUID(handler.getThing().getUID(), RfLinkBindingConstants.CHANNEL_SHUTTER),
                position);
    }

    private void sendCommand(Command command) {
        logger.debug("sending command " + command + " on " + handler.getThing().getUID());
        if (command != null) {
            commandProcessedEffective = command;
            try {
                RfLinkDevice device = RfLinkDeviceFactory.createDeviceFromType(handler.getThing().getThingTypeUID());
                device.initializeFromChannel(handler.getConfiguration(),
                        new ChannelUID(handler.getThing().getUID(), RfLinkBindingConstants.CHANNEL_SHUTTER), command);
                sendDeviceCommand(device);
            } catch (RfLinkException | RfLinkNotImpException e) {
                logger.error("Could not send Command " + command, e);
            }
        }
    }

    private void sendDeviceCommand(RfLinkDevice device) {
        try {
            handler.getBridgeHandler().sendPackets(device.buildPackets());
        } catch (RfLinkException e) {
            logger.error("Could not send Device event " + device + " on bridge " + handler.getBridgeHandler(), e);
        }
    }

    private Command getDirectionCommandFromMove(int diff) {
        Command direction;
        if (diff > 0) {
            direction = UpDownType.UP;
        } else if (diff < 0) {
            direction = UpDownType.DOWN;
        } else {
            direction = StopMoveType.STOP;
        }
        logger.debug("moving " + diff + " > " + direction);
        return direction;
    }

    private int getMoveFromTargetPosition(PercentType rawCommand) {
        PercentType currentPosition = positionFrom;
        PercentType targetPosition = rawCommand;
        int diff = targetPosition.intValue() - currentPosition.intValue();
        logger.debug("moving from " + currentPosition + " to " + targetPosition + " => " + diff);
        return diff;
    }

    private PercentType computeSnapshotPositionFromDelay() {
        // compute duration since previous command
        long effectiveDuration = System.currentTimeMillis() - timestampOnLastEvent;
        // compute displacement during duration
        long displacementPercentValue = Math.round(100.0 * effectiveDuration / shutterEffectiveDuration);
        int way = getWayFromCommand(commandProcessedEffective);
        int startPositionValue = positionFrom.intValue();
        // compute new position
        Long newPositionValue = startPositionValue + displacementPercentValue * way;
        newPositionValue = Math.min(100, Math.max(0, newPositionValue));
        int newPositionTrimedValue = newPositionValue.intValue();
        logger.debug("computed position=" + newPositionTrimedValue + " from " + startPositionValue + " after moving "
                + commandProcessedEffective + " for " + effectiveDuration + "ms");
        return new PercentType(newPositionTrimedValue);
    }

    private long computeDelayFromMoveValue(int moveValue) {
        long delay = Math.abs(moveValue) * shutterEffectiveDuration / 100;
        logger.debug("computed delay=" + delay + "ms for move=" + moveValue);
        return delay;
    }

    private int getWayFromCommand(Command command) {
        int way = 0;
        if (command instanceof UpDownType) {
            way = UpDownType.UP.equals(commandProcessedEffective) ? 1 : -1;
        }
        logger.debug("moving " + command + " : way=" + way);
        return way;
    }

    private void stopSchedulerStatus(boolean forceCancel) {
        if (schedulerStatus != null && !schedulerStatus.isCancelled()) {
            schedulerStatus.cancel(forceCancel);
            schedulerStatus = null;
        }
    }

    private void stopSchedulerTarget(boolean forceCancel) {
        if (schedulerTarget != null && !schedulerTarget.isCancelled()) {
            schedulerTarget.cancel(forceCancel);
            schedulerTarget = null;
        }
    }

}
