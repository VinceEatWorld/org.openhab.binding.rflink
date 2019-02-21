/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.device;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.message.RfLinkMessage;

/**
 * RfLink data class for humidity message.
 *
 * @author Marvyn Zalewski - Initial contribution
 */

public class RfLinkHumidityDevice extends RfLinkAbstractDevice {
    private static final String KEY_HUMIDITY = "HUM";
    private static final Collection<String> KEYS = Arrays.asList(KEY_HUMIDITY);

    public double humidity = 0;

    public RfLinkHumidityDevice() {
    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_HUMIDITY;
    }

    @Override
    public void initializeFromMessage(RfLinkMessage message) {
        super.initializeFromMessage(message);
        Map<String, String> values = getMessage().getValues();
        if (values.containsKey(KEY_HUMIDITY)) {
            humidity = Integer.parseInt(values.get(KEY_HUMIDITY));
        }
    }

    @Override
    public Collection<String> keys() {
        return KEYS;
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
