package me.cryo.zombierool.client;

import me.cryo.zombierool.core.manager.InteractableManager.Interactable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientInteractableManager {
    private static final Map<String, Interactable> interactables = new ConcurrentHashMap<>();

    public static void setInteractables(Map<String, Interactable> newInteractables) {
        interactables.clear();
        interactables.putAll(newInteractables);
    }

    public static Map<String, Interactable> getInteractables() {
        return interactables;
    }
}