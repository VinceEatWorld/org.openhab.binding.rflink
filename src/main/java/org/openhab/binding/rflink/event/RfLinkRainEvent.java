/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.event;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.config.RfLinkDeviceConfiguration;
import org.openhab.binding.rflink.message.RfLinkMessage;

/**
 * RfLink data class for rain message.
 *
 * @author Cyril Cauchois - Initial contribution
 */
public class RfLinkRainEvent extends RfLinkAbstractEvent {

    private static final String KEY_RAIN = "RAIN";
    private static final String KEY_RAIN_RATE = "RAINRATE";

    private static final List<String> KEYS = Arrays.asList(KEY_RAIN, KEY_RAIN_RATE);

    public double rain = 0;
    public double rainRate = 0;

    public RfLinkRainEvent() {

    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_RAIN;
    }

    @Override
    public void initializeFromMessage(RfLinkDeviceConfiguration config, RfLinkMessage message) {
        super.initializeFromMessage(config, message);
        Map<String, String> values = getMessage().getAttributes();
        if (values.containsKey(KEY_RAIN)) {
            rain = RfLinkDataParser.parseHexaToUnsignedInt(values.get(KEY_RAIN));
        }
        if (values.containsKey(KEY_RAIN_RATE)) {
            rainRate = RfLinkDataParser.parseHexaToUnsignedInt(values.get(KEY_RAIN));
        }
    }

    @Override
    public Predicate<RfLinkMessage> eligibleMessageFunction() {
        return (message) -> {
            return !Collections.disjoint(message.getAttributesKeys(), KEYS);
        };
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        map.put(RfLinkBindingConstants.CHANNEL_RAIN_TOTAL, new DecimalType(rain));
        map.put(RfLinkBindingConstants.CHANNEL_RAIN_RATE, new DecimalType(rainRate));
        return map;
    }

    @Override
    public String toString() {
        String str = super.toString();
        str += ", Rain Total = " + rain;
        str += ", Rain Rate = " + rainRate;
        return str;
    }

}
