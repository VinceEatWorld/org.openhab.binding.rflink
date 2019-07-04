/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.event;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.rflink.exceptions.RfLinkException;
import org.openhab.binding.rflink.exceptions.RfLinkNotImpException;
import org.openhab.binding.rflink.message.RfLinkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RFLink Message factory
 *
 * @author Cyril Cauchois - Initial contribution
 * @author Arjan Mels - Order of added keys is retained and search form first to last (to allow overlapping keywords to
 *         be handled properly)
 */
public class RfLinkEventFactory {

    private static Logger logger = LoggerFactory.getLogger(RfLinkEventFactory.class);

    private static LinkedHashMap<Predicate<RfLinkMessage>, Class<? extends RfLinkEvent>> MESSAGE_TO_EVENT_CLASS = new LinkedHashMap<>();
    private static HashMap<ThingTypeUID, Class<? extends RfLinkEvent>> THINGTYPE_TO_EVENT_CLASS = new HashMap<>();

    /**
     * Mapping of the various message classes.
     * Note that the order is important: first matching class will be used
     */
    static {
        addMappingOfClass(RfLinkEnergyEvent.class);
        addMappingOfClass(RfLinkWindEvent.class);
        addMappingOfClass(RfLinkRainEvent.class);
        addMappingOfClass(RfLinkColorEvent.class);
        // addMappingOfClass(RfLinkTemperatureDevice.class);
        addMappingOfClass(RfLinkRtsEvent.class);
        // addMappingOfClass(RfLinkHumidityDevice.class);
        addMappingOfClass(RfLinkTempHygroEvent.class);
        addMappingOfClass(RfLinkSwitchEvent.class); // Switch class last as it is most generic
    }

    private static void addMappingOfClass(Class<? extends RfLinkEvent> _class) {
        try {
            RfLinkEvent m = _class.newInstance();
            MESSAGE_TO_EVENT_CLASS.put(m.eligibleMessageFunction(), _class);
            THINGTYPE_TO_EVENT_CLASS.put(m.getThingType(), _class);
        } catch (InstantiationException | IllegalAccessException e) {
            logger.error("Could not map RfLinkEvent type : " + _class);
        }
    }

    public static RfLinkEvent createEventFromMessage(RfLinkMessage message)
            throws RfLinkException, RfLinkNotImpException {
        for (Map.Entry<Predicate<RfLinkMessage>, Class<? extends RfLinkEvent>> messageToEventEntry : MESSAGE_TO_EVENT_CLASS
                .entrySet()) {
            if (messageToEventEntry.getKey().test(message)) {
                Class<? extends RfLinkEvent> cl = messageToEventEntry.getValue();
                try {
                    Constructor<?> c = cl.getConstructor();
                    RfLinkEvent event = (RfLinkEvent) c.newInstance();
                    return event;
                } catch (Exception e) {
                    logger.error("Exception: {}", e);
                    throw new RfLinkException("unable to instanciate message object", e);
                }
            }
        }
        throw new RfLinkNotImpException("No message implementation found for packet " + message.rawMessage);
    }

    public static RfLinkEvent createEventFromType(ThingTypeUID thingType) throws RfLinkException {
        if (THINGTYPE_TO_EVENT_CLASS.containsKey(thingType)) {
            try {
                Class<?> cl = THINGTYPE_TO_EVENT_CLASS.get(thingType);
                Constructor<?> c = cl.getConstructor();
                return (RfLinkEvent) c.newInstance();
            } catch (Exception e) {
                throw new RfLinkException("Unable to instanciate message object", e);
            }
        }
        return null;
    }
}
