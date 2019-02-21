/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.device;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.openhab.binding.rflink.type.RfLinkTypeUtils;

/**
 * RfLink Abstract message - must be extended in specific implementations
 *
 * @author Cyril Cauchois - Initial contribution
 * @author cartemere - review Message management. add Reverse support for Switch/RTS
 * @author cartemere - Massive rework : split message vs device
 */
public abstract class RfLinkAbstractDevice implements RfLinkDevice {

    private boolean isCommandReversed = false;
    private RfLinkMessage message = null;

    @Override
    public void initializeFromMessage(RfLinkMessage message) {
        this.message = message;
    }

    @Override
    public void initializeFromChannel(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command command)
            throws RfLinkNotImpException, RfLinkException {
        throw new RfLinkNotImpException("Message handler for " + config + "/" + channelUID
                + " does not support command transmission " + command);
    }

    public void initBaseMessageFromChannel(RfLinkDeviceConfiguration config, ChannelUID channelUID, Command command)
            throws RfLinkNotImpException, RfLinkException {
        message = new RfLinkMessage(config, channelUID, command);
        this.isCommandReversed = config.isCommandReversed;
    }

    public RfLinkMessage getMessage() {
        return message;
    }

    @Override
    public Collection<String> buildMessages() {
        return Collections.singleton(getMessage().buildMessage(getCommandSuffix()));
    }

    // to override in subClasses if needed
    public String getCommandSuffix() {
        return null;
    }

    @Override
    public String getKey() {
        return getMessage().getDeviceKey();
    }

    @Override
    public String getProtocol() {
        return getMessage().getProtocol();
    }

    @Override
    public Map<String, State> getStates() {
        return Collections.<String, State> emptyMap();
    }

    @Override
    public State getState(String key) {
        return getStates().get(key);
    }

    protected Command getEffectiveCommand(Command inputCommand) {
        if (isCommandReversed) {
            // reverse the command
            Command effectiveCommand = (Command) RfLinkTypeUtils.getAntonym(inputCommand);
            if (effectiveCommand == null) {
                // no reverse available : defaulting
                return inputCommand;
            }
            return effectiveCommand;
        }
        return inputCommand;
    }

    @Override
    public String toString() {
        return "RfLinkAbstractDevice [message=" + message + ", isCommandReversed=" + isCommandReversed + ", ]";
    }

}
