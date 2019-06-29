package org.openhab.binding.rflink.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.openhab.binding.rflink.packet.RfLinkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RfLinkBridgeTxQueue {

    private Logger logger = LoggerFactory.getLogger(RfLinkBridgeTxQueue.class);

    private RfLinkBridgeHandler bridge = null;
    private Queue<Collection<RfLinkPacket>> queue = null;

    public RfLinkBridgeTxQueue(RfLinkBridgeHandler bridge) {
        this.bridge = bridge;
        this.queue = new LinkedBlockingQueue<Collection<RfLinkPacket>>();
    }

    public synchronized void enqueue(Collection<RfLinkPacket> outputPackets) throws IOException {
        boolean wasEmpty = queue.isEmpty();
        if (queue.offer(outputPackets)) {
            if (wasEmpty) {
                send();
            }
        } else {
            logger.error("Transmit queue overflow. Lost message: {}", outputPackets);
        }
    }

    public synchronized void send() throws IOException {
        while (!queue.isEmpty()) {
            Collection<RfLinkPacket> packets = queue.poll();
            bridge.getConnector().sendMessages(packets);
        }
    }
}
