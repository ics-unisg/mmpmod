package ch.unisg.mmpmod.processeventrouter;

public interface RouterService {

    void resolveAmbiguityAndPublishEvent(String json_events, String windowId);

    void publishUnambiguousEvent(String jsonEvent, String windowId);

}
