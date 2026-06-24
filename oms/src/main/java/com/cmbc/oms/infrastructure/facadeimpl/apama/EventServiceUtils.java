package com.cmbc.oms.infrastructure.facadeimpl.apama;

import com.apama.event.Event;
import com.apama.event.EventListenerAdapter;
import com.apama.event.IEventListener;
import com.apama.event.parser.EventType;
import com.apama.services.event.IEventService;
import com.apama.services.event.IEventServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Cly
 * @Date: 2026/01/22  16:16
 * @Description:
 */
@Component
public class EventServiceUtils {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    CorrelatorUtils correlatorUtils;
    AtomicBoolean isDestroyed = new AtomicBoolean(false);

    public EventServiceUtils() {
    }

    public void sendEvent(Object source, Event event) {
        IEventService eventService = this.correlatorUtils.getScenarioService().getEventService();
        try {
            eventService.sendEvent(event);
        } catch (RuntimeException var5) {
            this.logger.error("Error sending - source=" + source + " " + event + " -> " + var5, var5);
            throw var5;
        } catch (Throwable var6) {
            this.logger.error("Error sending - source=" + source + " " + event + " -> " + var6, var6);
            throw new RuntimeException("EventServiceUtils.sendEvent cause -> " + var6);
        }
    }

    public void sendEventToPm(Object source, Event event) {
        IEventService eventService = this.correlatorUtils.getPmScenarioService().getEventService();

        try {
            eventService.sendEvent(event);
            logger.info("sendEvent success - source=" + source + " " + event);
        } catch (RuntimeException var5) {
            this.logger.error("Error sending - source=" + source + " " + event + " -> " + var5, var5);
            throw var5;
        } catch (Throwable var6) {
            this.logger.error("Error sending - source=" + source + " " + event + " -> " + var6, var6);
            throw new RuntimeException("EventServiceUtils.sendEvent cause -> " + var6);
        }
    }

    public void sendEvents(Object source, List<Event> eventsToSend) {
        if (eventsToSend.size() > 0) {
            Iterator var3 = eventsToSend.iterator();

            while(var3.hasNext()) {
                Event event = (Event) var3.next();
                this.sendEvent(source, event);
            }
        }
    }

    public void addEventListener(String channel, EventType type, EventListenerAdapter listener) {
        IEventService eventService = this.correlatorUtils.getScenarioService().getEventService();

        try {
            this.logger.info("Adding listener - channel=" + channel + ", eventType=" + type.toString());
            IEventServiceChannel ourChannel = eventService.addChannel(channel, (Map)null);
            ourChannel.addEventListener(listener, type);
        } catch (RuntimeException var6) {
            this.logger.error("Error adding listener to %s ->%s ", channel, var6.getMessage());
            throw var6;
        } catch (Throwable var7) {
            this.logger.error("Error adding listener to %s ->%s ", channel, var7.getMessage());
            throw new RuntimeException("EventServiceUtils.addChannel cause -> " + var7.getMessage());
        }
    }

    public void removeEventListener(String channel, IEventListener iEventListener, EventType eventType) {
        IEventService eventService = this.correlatorUtils.getScenarioService().getEventService();

        try {
            IEventServiceChannel ourChannel = eventService.addChannel(channel, (Map)null);
            ourChannel.removeEventListener(iEventListener, eventType);
        } catch (RuntimeException var6) {
            throw var6;
        } catch (Throwable var7) {
            throw new RuntimeException("EventServiceUtils.addChannel cause -> " + var7);
        }
    }

    public void destoryed() {
        this.logger.info("EventServiceUtils is destoryed");
        IEventService eventService = this.correlatorUtils.getScenarioService().getEventService();
        if (eventService != null) {
            if (!this.isDestroyed.get()) {
                eventService.destroy();
            }

            this.isDestroyed.getAndSet(true);
        }
    }
}
