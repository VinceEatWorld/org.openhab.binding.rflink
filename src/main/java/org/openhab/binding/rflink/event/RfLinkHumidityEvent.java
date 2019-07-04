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
 * RfLink data class for humidity message.
 *
 * @author Marvyn Zalewski - Initial contribution
 */

public class RfLinkHumidityEvent extends RfLinkAbstractEvent {
    private static final String KEY_HUMIDITY = "HUM";

    public double humidity = 0;

    public RfLinkHumidityEvent() {
    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_HUMIDITY;
    }

    @Override
    public void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message) {
        super.initializeFromMessage(config, message);
        Map<String, String> values = getMessage().getAttributes();
        if (values.containsKey(KEY_HUMIDITY)) {
            humidity = Integer.parseInt(values.get(KEY_HUMIDITY));
        }
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        map.put(RfLinkBindingConstants.CHANNEL_HUMIDITY, new DecimalType(humidity));
        return map;
    }

    @Override
    public String toString() {
        String str = "";
        str += super.toString();
        str += ", Humidity = " + humidity;
        return str;
    }

}
