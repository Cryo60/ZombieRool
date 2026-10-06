package me.cryo.zombierool.block;

public enum SignalChannel {
    ALPHA("alpha", "Alpha"),
    BETA("beta", "Beta"),
    OMEGA("omega", "Omega"),
    ULTIMA("ultima", "Ultima");

    private final String id;
    private final String displayName;

    SignalChannel(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
