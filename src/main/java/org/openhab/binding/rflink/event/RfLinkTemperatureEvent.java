/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.event;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.message.RfLinkMessage;

/**
 * RfLink data class for temperature message.
 *
 * @author John Jore - Initial contribution
 */

public class RfLinkTemperatureEvent extends RfLinkAbstractEvent {
    private static final String KEY_TEMPERATURE = "TEMP";

    public double temperature = 0;

    public RfLinkTemperatureEvent() {
    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_TEMPERATURE;
    }

    @Override
    public void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message) {
        super.initializeFromMessage(config, message);
        Map<String, String> values = getMessage().getAttributes();
        if (values.containsKey(KEY_TEMPERATURE)) {
            temperature = RfLinkDataParser.parseHexaToSignedDecimal(values.get(KEY_TEMPERATURE));
        }
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        map.put(RfLinkBindingConstants.CHANNEL_TEMPERATURE, new DecimalType(temperature));
        return map;
    }

    @Override
    public String toString() {
        String str = super.toString();
        str += ", Temperature = " + temperature;
        return str;
    }
}
