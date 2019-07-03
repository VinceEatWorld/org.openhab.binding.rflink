/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.device;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.openhab.binding.rflink.type.RfLinkTypeUtils;

/**
 * RfLink data class for Somfy/RTS message.
 *
 * @author John Jore - Initial contribution
 * @author Arjan Mels - Added reception and debugged sending
 * @author cartemere - support RollerShutter
 */
public class RfLinkRtsDevice extends RfLinkAbstractDevice {

    public static String PROTOCOL_RTS = "RTS";
    private static final String KEY_CMD = "CMD";
    public Command command = null;
    public State shutter = null;

    public RfLinkRtsDevice() {
    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_RTS;
    }

    @Override
    public Predicate<RfLinkMessage> eligibleMessageFunction() {
        return (message) -> {
            return PROTOCOL_RTS.equals(message.getProtocol());
        };
    }

    @Override
    public String toString() {
        String str = "";
        str += super.toString();
        str += ", Shutter = " + shutter;
        str += ", Command = " + command + "]";
        return str;
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        if (shutter != null && !UnDefType.UNDEF.equals(shutter)) {
            if (!(getConfig().isRtsPositionTrackerEnabled())) {
                map.put(RfLinkBindingConstants.CHANNEL_SHUTTER, shutter);
            }
        }
        map.put(RfLinkBindingConstants.CHANNEL_COMMAND, command instanceof State ? (State) command : UnDefType.UNDEF);
        return map;
    }

    @Override
    public void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message) {
        super.initializeFromMessage(config, message);
        Map<String, String> values = getMessage().getAttributes();
        if (values.containsKey(KEY_CMD)) {
            command = (Command) RfLinkTypeUtils.getTypeFromStringValue(values.get(KEY_CMD));
            shutter = (State) RfLinkTypeUtils.getOnOffTypeFromType(command);
        }
    }

    @Override
    public void initializeFromChannel(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command triggeredCommand)
            throws RfLinkNotImpException, RfLinkException {
        super.initializeFromChannel(config, channelUID, triggeredCommand);
        command = getCommandAction(channelUID.getId(), triggeredCommand);
        if (!(getConfig().isRtsPositionTrackerEnabled())) {
            shutter = (State) RfLinkTypeUtils.getOnOffTypeFromType(command);
        }
    }

    @Override
    protected boolean handleCommandTransmission() {
        return true;
    }

    @Override
    public String getCommandSuffix() {
        return this.command.toString();
    }

    public Command getCommand() {
        return command;
    }

    public State getState() {
        return shutter;
    }

    public Command getCommandAction(String channelId, Type type) throws RfLinkException {
        Command command = null;
        switch (channelId) {
            case RfLinkBindingConstants.CHANNEL_COMMAND:
            case RfLinkBindingConstants.CHANNEL_SHUTTER:
                if (type instanceof StopMoveType) {
                    // STOP action : easy to handle
                    command = StopMoveType.STOP;
                } else if (type instanceof PercentType) {
                    // PercentType will be processed by the RTS Position handler (if configured)
                    command = (PercentType) type;
                } else {
                    // try to match UP/DOWN switch type from input type
                    Type switchType = RfLinkTypeUtils.getUpDownTypeFromType(type);
                    if (UnDefType.UNDEF.equals(switchType)) {
                        throw new RfLinkException("Channel " + channelId + " does not accept " + type);
                    } else {
                        command = (Command) switchType;
                    }
                }
                break;
            default:
                throw new RfLinkException("Channel " + channelId + " is not relevant here");
        }
        return getEffectiveCommand(command);
    }

}
